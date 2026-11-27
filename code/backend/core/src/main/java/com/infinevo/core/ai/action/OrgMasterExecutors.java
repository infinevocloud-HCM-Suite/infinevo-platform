package com.infinevo.core.ai.action;

import static com.infinevo.core.ai.action.OrgMasterCreateExecutor.ADDRESS;
import static com.infinevo.core.ai.action.OrgMasterCreateExecutor.CITY;
import static com.infinevo.core.ai.action.OrgMasterCreateExecutor.CODE;
import static com.infinevo.core.ai.action.OrgMasterCreateExecutor.NAME;
import static com.infinevo.core.ai.action.OrgMasterCreateExecutor.STATE;
import static com.infinevo.core.ai.action.OrgMasterCreateExecutor.STATE_CODE;
import static com.infinevo.core.ai.action.OrgMasterCreateExecutor.ZIP;
import static com.infinevo.core.ai.action.OrgMasterCreateExecutor.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.infinevo.core.org.DepartmentRequest;
import com.infinevo.core.org.DepartmentResponse;
import com.infinevo.core.org.DepartmentService;
import com.infinevo.core.org.DesignationRequest;
import com.infinevo.core.org.DesignationResponse;
import com.infinevo.core.org.DesignationService;
import com.infinevo.core.org.WorkLocationRequest;
import com.infinevo.core.org.WorkLocationResponse;
import com.infinevo.core.org.WorkLocationService;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** The three org master executors of W-114.28 steps 1-3, one bean each. */
final class OrgMasterExecutors {

    private OrgMasterExecutors() {}

    @Component
    static class Department extends OrgMasterCreateExecutor {

        static final String ACTION_CODE = "core.org.department.create";

        private final DepartmentService service;

        Department(DepartmentService service) {
            this.service = Objects.requireNonNull(service, "service must not be null");
        }

        @Override
        public String actionCode() {
            return ACTION_CODE;
        }

        @Override
        String kind() {
            return "department";
        }

        @Override
        String screen() {
            return "/org/departments";
        }

        @Override
        List<String> names() {
            return service.list(false).stream().map(DepartmentResponse::name).toList();
        }

        @Override
        Created create(JsonNode p) {
            DepartmentResponse r = service.create(new DepartmentRequest(text(p, CODE), text(p, NAME), true));
            return new Created(r.id().toString(), r.code());
        }
    }

    @Component
    static class Designation extends OrgMasterCreateExecutor {

        static final String ACTION_CODE = "core.org.designation.create";

        private final DesignationService service;

        Designation(DesignationService service) {
            this.service = Objects.requireNonNull(service, "service must not be null");
        }

        @Override
        public String actionCode() {
            return ACTION_CODE;
        }

        @Override
        String kind() {
            return "designation";
        }

        @Override
        String screen() {
            return "/org/designations";
        }

        @Override
        List<String> names() {
            return service.list(false).stream().map(DesignationResponse::name).toList();
        }

        @Override
        Created create(JsonNode p) {
            DesignationResponse r = service.create(new DesignationRequest(text(p, CODE), text(p, NAME), true));
            return new Created(r.id().toString(), r.code());
        }
    }

    @Component
    static class Location extends OrgMasterCreateExecutor {

        static final String ACTION_CODE = "core.org.location.create";

        private final WorkLocationService service;

        Location(WorkLocationService service) {
            this.service = Objects.requireNonNull(service, "service must not be null");
        }

        @Override
        public String actionCode() {
            return ACTION_CODE;
        }

        @Override
        String kind() {
            return "work location";
        }

        @Override
        String screen() {
            return "/org/work-locations";
        }

        @Override
        List<String> names() {
            return service.list(false).stream().map(WorkLocationResponse::name).toList();
        }

        @Override
        Created create(JsonNode p) {
            WorkLocationResponse r = service.create(new WorkLocationRequest(
                    text(p, CODE),
                    text(p, NAME),
                    text(p, ADDRESS),
                    null,
                    text(p, CITY),
                    text(p, STATE),
                    text(p, STATE_CODE),
                    text(p, ZIP),
                    "IN",
                    false,
                    true));
            return new Created(r.id().toString(), r.code());
        }
    }
}
