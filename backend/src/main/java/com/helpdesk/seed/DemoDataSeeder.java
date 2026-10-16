package com.helpdesk.seed;

import com.helpdesk.comment.CommentService;
import com.helpdesk.comment.dto.CreateCommentRequest;
import com.helpdesk.organization.Organization;
import com.helpdesk.organization.OrganizationService;
import com.helpdesk.security.AuthenticatedUser;
import com.helpdesk.security.Role;
import com.helpdesk.ticket.domain.CommentVisibility;
import com.helpdesk.ticket.domain.TicketPriority;
import com.helpdesk.ticket.domain.TicketStatus;
import com.helpdesk.ticket.service.TicketService;
import com.helpdesk.ticket.web.dto.CreateTicketRequest;
import com.helpdesk.ticket.web.dto.TicketDetail;
import com.helpdesk.user.User;
import com.helpdesk.user.UserService;
import com.helpdesk.user.dto.CreateUserRequest;
import com.helpdesk.user.dto.UserDetail;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seeds two organizations — Acme and Globex — so reviewers can see tenant isolation right away.
 * Each has 1 admin, 2 agents, 3 customers and 25 tickets in mixed states with public and internal
 * comments and history. Idempotent: an organization that already exists is skipped.
 * Everything goes through the real services, so the data obeys every business rule.
 *
 * <p>All demo accounts use the password {@value #PASSWORD}.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

    static final String PASSWORD = "Password123!";
    private static final int TICKETS_PER_ORG = 25;
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final String[][] TICKETS = {
            {"Cannot reset password", "The password reset email never arrives. I checked spam as well."},
            {"Invoice PDF is blank", "Downloading last month's invoice gives an empty PDF."},
            {"App crashes on login", "The mobile app closes immediately after I enter my credentials."},
            {"Request to change billing address", "We moved offices; please update the billing address on our account."},
            {"Slow dashboard loading", "The dashboard takes more than 30 seconds to load every morning."},
            {"Two-factor codes not accepted", "My authenticator codes are rejected as invalid."},
            {"Export to CSV missing columns", "The CSV export does not include the custom fields."},
            {"Add a new team member", "Please add our new colleague to the account."},
            {"Webhook deliveries failing", "Our endpoint receives no webhook calls since yesterday."},
            {"Typo on the pricing page", "The word 'monthly' is misspelled on the pricing page."},
            {"Unable to upload profile picture", "Uploading a PNG returns an unknown error."},
            {"Search returns no results", "Searching for an existing order number returns nothing."},
            {"Duplicate charge on credit card", "We were charged twice for the October subscription."},
            {"Feature request: dark mode", "Would love a dark theme for the web app."},
            {"Email notifications delayed", "Notifications arrive hours after the event."},
            {"API rate limit too low", "We hit the rate limit during our nightly sync."},
            {"Cannot delete old project", "The delete button on archived projects is greyed out."},
            {"Time zone shown incorrectly", "All timestamps appear in UTC instead of my local time."},
            {"SSO configuration question", "How do we connect our identity provider?"},
            {"Report totals do not match", "The monthly report total differs from the sum of line items."},
    };

    private final OrganizationService organizationService;
    private final UserService userService;
    private final TicketService ticketService;
    private final CommentService commentService;

    public DemoDataSeeder(OrganizationService organizationService, UserService userService,
                          TicketService ticketService, CommentService commentService) {
        this.organizationService = organizationService;
        this.userService = userService;
        this.ticketService = ticketService;
        this.commentService = commentService;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed("Acme Corp", "acme", "Alice Admin", new String[]{"Bob Agent", "Brenda Agent"},
                new String[]{"Carol Customer", "Chris Customer", "Cathy Customer"}, 42L);
        seed("Globex Inc", "globex", "Gina Admin", new String[]{"Gary Agent", "Grace Agent"},
                new String[]{"Hank Customer", "Holly Customer", "Hugo Customer"}, 7L);
    }

    private void seed(String name, String slug, String adminName, String[] agentNames, String[] customerNames, long randomSeed) {
        if (organizationService.findBySlug(slug).isPresent()) {
            log.info("Demo organization '{}' already exists — skipping", slug);
            return;
        }
        Organization org = organizationService.register(name, slug);
        User admin = userService.createInitialAdmin(org.getId(), adminName, "admin@" + slug + ".test", PASSWORD);
        AuthenticatedUser adminActor = new AuthenticatedUser(admin.getId(), org.getId(), Role.ORG_ADMIN, admin.getEmail());

        List<AuthenticatedUser> agents = new ArrayList<>();
        for (int i = 0; i < agentNames.length; i++) {
            agents.add(createUser(adminActor, agentNames[i], "agent" + (i + 1) + "@" + slug + ".test", Role.AGENT));
        }
        List<AuthenticatedUser> customers = new ArrayList<>();
        for (int i = 0; i < customerNames.length; i++) {
            customers.add(createUser(adminActor, customerNames[i], "customer" + (i + 1) + "@" + slug + ".test", Role.CUSTOMER));
        }

        Random random = new Random(randomSeed);
        TicketPriority[] priorities = TicketPriority.values();
        for (int i = 0; i < TICKETS_PER_ORG; i++) {
            String[] template = TICKETS[i % TICKETS.length];
            AuthenticatedUser customer = customers.get(i % customers.size());
            AuthenticatedUser agent = agents.get(i % agents.size());
            TicketPriority priority = priorities[random.nextInt(priorities.length)];
            String subject = i < TICKETS.length ? template[0] : template[0] + " (follow-up)";

            // Every third ticket is raised by an agent on behalf of the customer.
            TicketDetail ticket = i % 3 == 0
                    ? ticketService.create(agent, new CreateTicketRequest(subject, template[1], priority, customer.userId()))
                    : ticketService.create(customer, new CreateTicketRequest(subject, template[1], null, null));

            evolve(ticket, i, customer, agent, adminActor);
        }
        log.info("Seeded demo organization '{}' with {} tickets", slug, TICKETS_PER_ORG);
    }

    /** Moves tickets through a spread of realistic states. */
    private void evolve(TicketDetail ticket, int i, AuthenticatedUser customer, AuthenticatedUser agent, AuthenticatedUser admin) {
        int scenario = i % 6;
        if (scenario == 0) {
            return;   // stays OPEN and unassigned
        }
        ticketService.assign(scenario == 5 ? admin : agent, ticket.id(), agent.userId());
        comment(agent, ticket, "Thanks for reaching out — we are looking into it.", CommentVisibility.PUBLIC);
        if (scenario == 1) {
            return;   // OPEN, assigned
        }
        ticketService.changeStatus(agent, ticket.id(), TicketStatus.IN_PROGRESS, null);
        comment(agent, ticket, "Reproduced on staging. Likely related to last week's deploy.", CommentVisibility.INTERNAL);
        if (scenario == 2) {
            comment(customer, ticket, "Any update on this?", CommentVisibility.PUBLIC);
            return;   // IN_PROGRESS
        }
        if (scenario == 3) {
            ticketService.changeStatus(agent, ticket.id(), TicketStatus.ON_HOLD, "Waiting for your confirmation before we continue.");
            return;   // ON_HOLD
        }
        ticketService.changeStatus(agent, ticket.id(), TicketStatus.RESOLVED, "This has been fixed. Please let us know if it happens again.");
        if (scenario == 4) {
            return;   // RESOLVED
        }
        ticketService.changeStatus(customer, ticket.id(), TicketStatus.CLOSED, "Confirmed fixed, thank you!");
    }

    private void comment(AuthenticatedUser author, TicketDetail ticket, String body, CommentVisibility visibility) {
        commentService.add(author, ticket.id(), new CreateCommentRequest(body, visibility));
    }

    private AuthenticatedUser createUser(AuthenticatedUser admin, String fullName, String email, Role role) {
        UserDetail user = userService.create(admin, new CreateUserRequest(fullName, email, PASSWORD, role));
        return new AuthenticatedUser(user.id(), admin.organizationId(), role, user.email());
    }
}
