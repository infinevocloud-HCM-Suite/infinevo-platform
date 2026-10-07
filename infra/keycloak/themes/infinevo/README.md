# `infinevo` Keycloak theme

Puts "Infinevo Cloud" where stock Keycloak says "Keycloak": browser tab titles, favicons, the
admin and account console logos, and the few messages that name the product. Everything else is
inherited from the stock themes.

| Type | Parent | Changes |
|---|---|---|
| `admin` | `keycloak.v2` | title, favicon, masthead logo |
| `account` | `keycloak.v3` | title, favicon, masthead logo, four messages |
| `login` | `keycloak` | favicon; `infinevo.css` stops a realm's `kc-logo-text` heading showing the Keycloak logo (the heading text is the realm's display name) |
| `email` | `keycloak` | SMTP test subject |

It is the server default (`KC_SPI_THEME_DEFAULT=infinevo`, set in `infra/docker/keycloak.Dockerfile`
and `infra/docker/compose.yml`), so it applies to every realm that names no theme of its own -
no realm setting and no re-import is needed. A realm that sets `loginTheme`, `adminTheme`,
`accountTheme` or `emailTheme` overrides it.
