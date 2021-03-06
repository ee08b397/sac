package com.erfangc.sac.backend.tests;

import com.erfangc.sac.interfaces.*;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.junit.Assert.*;

public class BackendTestBase {

    protected SimpleAccessControl sac;

    private Group allEmployees() {
        return ImmutableGroup.
                builder()
                .id("all employees")
                .name("All Employees")
                .build();
    }

    private IdentityPolicy managePayPolicy() {
        return ImmutableIdentityPolicy
                .builder()
                .id("manage pay")
                .actions(asList("increase", "decrease"))
                .resource("/org/employees/*/pay")
                .build();
    }

    private IdentityPolicy serverLoginPolicy() {
        return ImmutableIdentityPolicy
                .builder()
                .id("server login")
                .actions(singletonList("login"))
                .resource("/org/servers/*")
                .build();
    }

    private IdentityPolicy employeeReadOnlyPolicy() {
        return ImmutableIdentityPolicy
                .builder()
                .id("employee read only")
                .actions(singletonList("read"))
                .resource("/org/employees/*")
                .build();
    }

    private Group humanResources() {
        return ImmutableGroup
                .builder()
                .name("Human Resources")
                .id("hr")
                .build();
    }

    private Group networkAdmins() {
        return ImmutableGroup
                .builder()
                .name("Network Administrators")
                .id("network admins")
                .build();
    }

    protected void initializePolicyBackendStates() {
        final Group networkAdmins = networkAdmins();
        sac.createGroup(networkAdmins);

        final Group humanResources = humanResources();
        sac.createGroup(humanResources);

        final Group allEmployees = allEmployees();
        sac.createGroup(allEmployees);

        final IdentityPolicy employeeReadOnlyIdentityPolicy = employeeReadOnlyPolicy();
        sac.createPolicy(employeeReadOnlyIdentityPolicy);
        sac.assignPolicy(employeeReadOnlyIdentityPolicy.id(), allEmployees.id());

        sac.assignPrincipalToGroup(allEmployees.id(), humanResources.id(), true);
        sac.assignPrincipalToGroup(allEmployees.id(), networkAdmins.id(), true);

        final IdentityPolicy serverLoginIdentityPolicy = serverLoginPolicy();
        sac.createPolicy(serverLoginIdentityPolicy);
        sac.assignPolicy(serverLoginIdentityPolicy.id(), networkAdmins.id());

        final IdentityPolicy managePayIdentityPolicy = managePayPolicy();
        sac.createPolicy(managePayIdentityPolicy);
        sac.assignPolicy(managePayIdentityPolicy.id(), humanResources.id());
    }

    @Test
    public void createGroup() {
        final ImmutableGroup group = ImmutableGroup.builder().id("new group").name("New Group").build();
        sac.createGroup(group);
        final Group result = sac.getGroup(group.id());
        assertEquals(group.id(), result.id());
    }

    @Test
    public void getGroup() {
        final Group group = sac.getGroup(networkAdmins().id());
        assertEquals("Network Administrators", group.name());
    }

    @Test
    public void update() {
        final String groupId = networkAdmins().id();
        final Group group = sac.getGroup(groupId);
        sac.updateGroup(
                ImmutableGroup
                        .copyOf(group)
                        .withName("Foo")
        );
        final Group result = sac.getGroup(groupId);
        assertEquals("Foo", result.name());
    }

    @Test
    public void deleteGroup() {
        sac.deleteGroup(networkAdmins().id());
        final Group group = sac.getGroup(networkAdmins().id());
        assertNull(group);
    }

    @Test
    public void assignPrincipalToGroup() {
        final Group group = networkAdmins();
        sac.assignPrincipalToGroup(group.id(), "john");
        final Group result = sac.getGroup(group.id());
        final Optional<List<GroupAssignment>> assignments = result.assignments();
        assertTrue(assignments.isPresent());
        final Optional<GroupAssignment> john = assignments.get().stream().filter(r -> r.principal().equals("john")).findFirst();
        assertTrue(john.isPresent());
    }

    @Test
    public void unassignPrincipalToGroup() {
        final Group group = networkAdmins();
        sac.assignPrincipalToGroup(group.id(), "john");
        final Group result = sac.getGroup(group.id());
        final Optional<List<GroupAssignment>> assignments = result.assignments();
        assertTrue(assignments.isPresent());
        final Optional<GroupAssignment> john = assignments.get().stream().filter(r -> r.principal().equals("john")).findFirst();
        assertTrue(john.isPresent());
        sac.unassignPrincipalFromGroup(group.id(), "john");
        final Group result2 = sac.getGroup(group.id());
        final Optional<List<GroupAssignment>> assignments2 = result2.assignments();
        assertTrue(assignments2.isPresent());
        final Optional<GroupAssignment> john2 = assignments2.get().stream().filter(r -> r.principal().equals("john")).findFirst();
        assertFalse(john2.isPresent());
    }

    @Test
    public void unassignPolicy() {
        final Group networkAdmins = networkAdmins();
        sac.assignPrincipalToGroup(networkAdmins.id(), "john");
        final ImmutableAuthorizationRequest request = ImmutableAuthorizationRequest
                .builder()
                .id("abc")
                .action("login")
                .resource("/org/servers/server1")
                .principal("john")
                .build();
        assertEquals(AuthorizationStatus.Permitted, sac.authorize(request).status());
        sac.unAssignPolicy(serverLoginPolicy().id(), networkAdmins.id());
        assertEquals(AuthorizationStatus.Denied, sac.authorize(request).status());
    }

    @Test
    public void getGroupTree() {
        final Node root = sac.getGroupTree(allEmployees().id());
        assertNotNull(root);
        assertEquals(2, root.getChildren().size());
    }

    @Test
    public void getAllPrincipalsForGroup() {
        final Group group = networkAdmins();
        sac.assignPrincipalToGroup(group.id(), "john");
        sac.assignPrincipalToGroup(group.id(), "jsmith");
        final List<String> principals = sac.getAllPrincipalsForGroup(group.id());
        assertTrue(principals.contains("john"));
        assertTrue(principals.contains("jsmith"));
    }

    @Test
    public void getGroupMembership() {
        final Group networkAdmins = networkAdmins();
        final Group hr = humanResources();
        sac.assignPrincipalToGroup(networkAdmins.id(), "john");
        sac.assignPrincipalToGroup(hr.id(), "john");
        final List<String> groups = sac.getGroupMembership("john");
        assertEquals(2, groups.size());
        assertTrue(groups.contains(networkAdmins.id()));
        assertTrue(groups.contains(hr.id()));
    }

    @Test
    public void createPolicy() {
        IdentityPolicy newIdentityPolicy = ImmutableIdentityPolicy
                .builder()
                .id("new policy")
                .resource("/foo/bar")
                .actions(singletonList("*"))
                .build();
        sac.createPolicy(newIdentityPolicy);
        final IdentityPolicy identityPolicy = sac.getPolicy(newIdentityPolicy.id());
        assertEquals("new policy", identityPolicy.id());
    }

    @Test
    public void getPolicy() {
        final IdentityPolicy identityPolicy = sac.getPolicy(managePayPolicy().id());
        assertEquals("manage pay", identityPolicy.id());
    }

    @Test
    public void updatePolicy() {
        final IdentityPolicy identityPolicy = managePayPolicy();
        final ImmutableIdentityPolicy updated = ImmutableIdentityPolicy.copyOf(identityPolicy).withName("Foobar");
        sac.updatePolicy(updated);
        final IdentityPolicy result = sac.getPolicy(updated.id());
        assertTrue(result.name().isPresent());
        assertEquals("Foobar", result.name().get());
    }

    @Test
    public void deletePolicy() {
        final String policyId = managePayPolicy().id();
        sac.deletePolicy(policyId);
        final IdentityPolicy identityPolicy = sac.getPolicy(policyId);
        assertNull(identityPolicy);
    }

    @Test
    public void authorizeHrToManagePay() {
        final Group humanResources = humanResources();
        final Group networkAdmins = networkAdmins();
        final String hrGuy = "hr guy";
        final String itGuy = "it guy";
        sac.assignPrincipalToGroup(humanResources.id(), hrGuy);
        sac.assignPrincipalToGroup(networkAdmins.id(), itGuy);

        final ImmutableAuthorizationRequest authorizationRequest = ImmutableAuthorizationRequest
                .builder()
                .id("test request")
                .action("increase")
                .principal(hrGuy)
                .resource("/org/employees/jsmith/pay")
                .build();
        final AuthorizationResponse authorizationResponse = sac.authorize(authorizationRequest);

        assertEquals(AuthorizationStatus.Permitted, authorizationResponse.status());

        // hrGuy can no longer change pay after he has been removed from group
        sac.unassignPrincipalFromGroup(humanResources.id(), hrGuy);

        final AuthorizationResponse authorizationResponse1 = sac.authorize(authorizationRequest);
        assertEquals(AuthorizationStatus.Denied, authorizationResponse1.status());

        // itGuy shouldn't be able to manage pay either
        final AuthorizationResponse authorizationResponse2 = sac.authorize(authorizationRequest.withPrincipal(itGuy));
        assertEquals(AuthorizationStatus.Denied, authorizationResponse2.status());
    }

    @Test
    public void authorizeWithDirectlyAssignedPolicy() {
        final String hrGuy = "hr guy";
        // both itGuy and hrGuy should be able to access an employee record
        final ImmutableAuthorizationRequest authorizationRequest = ImmutableAuthorizationRequest
                .builder()
                .id("test request")
                .action("login")
                .principal(hrGuy)
                .resource("/org/servers/server1")
                .build();
        assertEquals(AuthorizationStatus.Denied, sac.authorize(authorizationRequest).status());
        sac.assignPolicy(serverLoginPolicy().id(), hrGuy);
        assertEquals(AuthorizationStatus.Permitted, sac.authorize(authorizationRequest).status());
    }

    @Test
    public void authorizeAgainstResourcePolicy() {
        String hrGuy = "hr guy";
        final ImmutableAuthorizationRequest request = ImmutableAuthorizationRequest
                .builder()
                .id("test request")
                .action("read")
                .resource("/books/book1")
                .principal(hrGuy)
                .build();
        final Group humanResources = humanResources();
        sac.assignPrincipalToGroup(humanResources.id(), hrGuy);
        assertEquals(AuthorizationStatus.Denied, sac.authorize(request).status());

        sac.grantActions("/books/book1", hrGuy, singleton("read"));
        assertEquals(AuthorizationStatus.Permitted, sac.authorize(request).status());

        sac.revokeActions("/books/book1", hrGuy, singleton("read"));
        assertEquals(AuthorizationStatus.Denied, sac.authorize(request).status());
    }

    @Test
    public void authorizeAgainstResourcePolicyMultiActions() {
        String hrGuy = "hr guy";
        final ImmutableAuthorizationRequest request = ImmutableAuthorizationRequest
                .builder()
                .id("test request")
                .action("write")
                .resource("/books/book1")
                .principal(hrGuy)
                .build();
        final Group humanResources = humanResources();
        sac.assignPrincipalToGroup(humanResources.id(), hrGuy);
        assertEquals(AuthorizationStatus.Denied, sac.authorize(request).status());

        Set<String> actions = readAndWrite();
        sac.grantActions("/books/book1", hrGuy, actions);
        assertEquals(AuthorizationStatus.Permitted, sac.authorize(request).status());

        sac.revokeActions("/books/book1", hrGuy, singleton("write"));
        assertEquals(AuthorizationStatus.Denied, sac.authorize(request).status());
        assertEquals(AuthorizationStatus.Permitted, sac.authorize(request.withAction("read")).status());
    }

    @Test
    public void authorizeAgainstResourcePolicyMultiPrincipals() {
        final Group humanResources = humanResources();
        final Group networkAdmins = networkAdmins();
        String hrGuy = "hr guy";
        String itGuy = "it guy";

        Set<String> actions = readAndWrite();

        final ImmutableAuthorizationRequest request = ImmutableAuthorizationRequest
                .builder()
                .id("test request")
                .action("write")
                .resource("/books/book1")
                .principal(hrGuy)
                .build();

        sac.assignPrincipalToGroup(humanResources.id(), hrGuy);
        sac.assignPrincipalToGroup(networkAdmins.id(), itGuy);

        assertEquals(AuthorizationStatus.Denied, sac.authorize(request).status());
        assertEquals(AuthorizationStatus.Denied, sac.authorize(request.withPrincipal(itGuy)).status());

        sac.grantActions("/books/book1", hrGuy, actions);
        sac.grantActions("/books/book1", itGuy, actions);
        assertEquals(AuthorizationStatus.Permitted, sac.authorize(request).status());
        assertEquals(AuthorizationStatus.Permitted, sac.authorize(request.withPrincipal(itGuy)).status());

        sac.revokeActions("/books/book1", hrGuy, singleton("write"));
        assertEquals(AuthorizationStatus.Permitted, sac.authorize(request.withPrincipal(itGuy)).status());
        assertEquals(AuthorizationStatus.Denied, sac.authorize(request).status());
        assertEquals(AuthorizationStatus.Permitted, sac.authorize(request.withAction("read")).status());
    }

    @Test
    public void authorizeAgainstResourcePolicyUsingGroups() {
        final Group humanResources = humanResources();
        String hrGuy = "hr guy";

        Set<String> actions = readAndWrite();

        final ImmutableAuthorizationRequest request = ImmutableAuthorizationRequest
                .builder()
                .id("test request")
                .action("write")
                .resource("/books/book1")
                .principal(hrGuy)
                .build();

        assertEquals(AuthorizationStatus.Denied, sac.authorize(request).status());
        sac.assignPrincipalToGroup(humanResources.id(), hrGuy);

        sac.grantActions("/books/book1", humanResources.id(), actions);
        assertEquals(AuthorizationStatus.Permitted, sac.authorize(request).status());

        sac.revokeActions("/books/book1", humanResources.id(), singleton("write"));
        assertEquals(AuthorizationStatus.Denied, sac.authorize(request).status());
        assertEquals(AuthorizationStatus.Permitted, sac.authorize(request.withAction("read")).status());

        /*
        Our principal cannot delete books, but let's grant that action
         */
        assertEquals(AuthorizationStatus.Denied, sac.authorize(request.withAction("delete")).status());
        sac.grantActions("/books/book1", humanResources.id(), singleton("delete"));
        assertEquals(AuthorizationStatus.Permitted, sac.authorize(request.withAction("delete")).status());

        /*
        Now let's remove that
         */
        sac.revokeActions("/books/book1", humanResources.id(), singleton("delete"));
        assertEquals(AuthorizationStatus.Denied, sac.authorize(request.withAction("delete")).status());
    }

    @Test
    public void authorizeAgainstResourcePolicyUsingGroupsTransitively() {
        final Group humanResources = humanResources();
        final Group allEmployees = allEmployees();
        String hrGuy = "hr guy";
        sac.assignPrincipalToGroup(humanResources.id(), hrGuy);

        Set<String> actions = readAndWrite();

        final ImmutableAuthorizationRequest request = ImmutableAuthorizationRequest
                .builder()
                .id("test request")
                .action("write")
                .resource("/books/book1")
                .principal(hrGuy)
                .build();

        assertEquals(AuthorizationStatus.Denied, sac.authorize(request).status());

        sac.grantActions("/books/book1", allEmployees.id(), actions);
        assertEquals(AuthorizationStatus.Permitted, sac.authorize(request).status());

        sac.revokeActions("/books/book1", allEmployees.id(), singleton("write"));
        assertEquals(AuthorizationStatus.Denied, sac.authorize(request).status());
        assertEquals(AuthorizationStatus.Permitted, sac.authorize(request.withAction("read")).status());
    }

    private Set<String> readAndWrite() {
        Set<String> actions = new HashSet<>();
        actions.add("write");
        actions.add("read");
        return actions;
    }

    @Test
    public void authorizeActionsTransitively() {
        final Group humanResources = humanResources();
        final Group networkAdmins = networkAdmins();
        final String hrGuy = "hr guy";
        final String itGuy = "it guy";
        sac.assignPrincipalToGroup(humanResources.id(), hrGuy);
        sac.assignPrincipalToGroup(networkAdmins.id(), itGuy);
        // both itGuy and hrGuy should be able to access an employee record
        final ImmutableAuthorizationRequest authorizationRequest = ImmutableAuthorizationRequest
                .builder()
                .id("test request")
                .action("read")
                .principal(hrGuy)
                .resource("/org/employees/jackjones")
                .build();
        final AuthorizationResponse authorizationResponse = sac.authorize(authorizationRequest);
        assertEquals(AuthorizationStatus.Permitted, authorizationResponse.status());

        final AuthorizationResponse authorizationResponse1 = sac.authorize(authorizationRequest.withPrincipal(itGuy));
        assertEquals(AuthorizationStatus.Permitted, authorizationResponse1.status());

        // but both still cannot perform a function that is not in their job role
        final AuthorizationResponse authorizationResponse2 = sac.authorize(authorizationRequest.withAction("delete"));
        assertEquals(AuthorizationStatus.Denied, authorizationResponse2.status());
    }
}
