package com.infinevo.shared.tenant;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * DataSource proxy that binds {@link TenantContext} to physical JDBC connections.
 *
 * <p><strong>The binding happens at first use of the connection, not at pool checkout.</strong>
 * Hikari hands out connections with auto-commit on, and Spring switches auto-commit off only
 * <em>after</em> the connection has been handed over. Binding at checkout therefore fell back to
 * {@code set_config(..., false)} — a PostgreSQL <em>session</em> write. Hikari does not reset
 * custom session settings when a connection returns to the pool, so the next borrower of that
 * connection started out bound to the previous tenant: a cross-tenant leak (finding F-10).
 *
 * <p>So {@link #getConnection()} returns the real connection wrapped in a {@link Proxy}. The first
 * {@code createStatement} / {@code prepareStatement} / {@code prepareCall} on that wrapper — by
 * which time the transaction has begun and auto-commit is off — routes through
 * {@link TenantDatabaseInterceptor#bindTenantToConnection(Connection)}, and so through
 * {@link TenantContext#setForConnection(Connection)}, which always writes {@code is_local = true}
 * and refuses an auto-commit connection ({@code D-57}). The SQL lives in exactly one place and the
 * {@code D-57} guard cannot be bypassed (finding F-11).
 *
 * <p>The "already bound" flag is cleared on {@code commit}, {@code rollback}, {@code close} and
 * {@code setAutoCommit(true)}, so a second transaction on the same pooled connection rebinds
 * rather than silently running unbound.
 */
public class TenantBindingDataSourceProxy extends DelegatingDataSource {

    public TenantBindingDataSourceProxy(DataSource targetDataSource) {
        super(targetDataSource);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return wrapConnection(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrapConnection(super.getConnection(username, password));
    }

    /** Wraps {@code target} so the tenant is bound lazily, at the first statement. */
    static Connection wrapConnection(Connection target) {
        if (target == null) {
            return null;
        }
        return (Connection) Proxy.newProxyInstance(
                TenantBindingDataSourceProxy.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                new TenantBindingConnectionHandler(target));
    }

    /**
     * Binds the tenant on the first statement created in each transaction and delegates
     * everything else to the real connection untouched.
     */
    static final class TenantBindingConnectionHandler implements InvocationHandler {

        /** First use of the connection in a transaction — bind before delegating. */
        private static final Set<String> STATEMENT_METHODS =
                Set.of("createStatement", "prepareStatement", "prepareCall");

        /** Transaction boundaries — the transaction-local binding is gone, so rebind next time. */
        private static final Set<String> BOUNDARY_METHODS = Set.of("commit", "rollback", "close");

        private final Connection target;
        private boolean bound;

        TenantBindingConnectionHandler(Connection target) {
            this.target = target;
        }

        /** The real connection behind the proxy. */
        Connection getTarget() {
            return target;
        }

        /** Whether this connection has already been bound in the current transaction. */
        boolean isBound() {
            return bound;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();

            switch (name) {
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(target);
                case "toString":
                    return "Tenant-binding proxy for " + target;
                case "unwrap": {
                    Class<?> iface = (Class<?>) args[0];
                    return iface.isInstance(target) ? target : target.unwrap(iface);
                }
                case "isWrapperFor": {
                    Class<?> iface = (Class<?>) args[0];
                    return iface.isInstance(target) || target.isWrapperFor(iface);
                }
                default:
                    break;
            }

            if (STATEMENT_METHODS.contains(name)) {
                bindIfNeeded();
            } else if (BOUNDARY_METHODS.contains(name)) {
                bound = false;
            } else if ("setAutoCommit".equals(name) && args != null && Boolean.TRUE.equals(args[0])) {
                bound = false;
            }

            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        }

        /**
         * Binds once per transaction. Deliberately does nothing when no tenant is bound — the
         * membership lookup in {@link TenantMembershipService} runs before the filter binds one.
         */
        private void bindIfNeeded() throws SQLException {
            if (!bound && TenantContext.isBound()) {
                TenantDatabaseInterceptor.bindTenantToConnection(target);
                bound = true;
            }
        }
    }
}
