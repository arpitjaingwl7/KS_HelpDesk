package com.helpdesk.ticket.web;

import com.helpdesk.ticket.domain.Ticket;
import com.helpdesk.ticket.domain.TicketPermissions;
import com.helpdesk.ticket.domain.TicketStatus;
import com.helpdesk.ticket.web.dto.TicketDetail;
import com.helpdesk.ticket.web.dto.TicketSummary;
import com.helpdesk.ticket.web.dto.TicketUser;
import com.helpdesk.user.User;
import com.helpdesk.user.dto.UserRef;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Entity → DTO mapping (MapStruct). Entities never leave the service layer. */
@Mapper
public interface TicketMapper {

    TicketSummary toSummary(Ticket ticket);

    @Mapping(target = "id", source = "ticket.id")
    @Mapping(target = "createdAt", source = "ticket.createdAt")
    @Mapping(target = "updatedAt", source = "ticket.updatedAt")
    @Mapping(target = "version", source = "ticket.version")
    @Mapping(target = "allowedTransitions", source = "allowedTransitions")
    @Mapping(target = "permissions", source = "permissions")
    TicketDetail toDetail(Ticket ticket, List<TicketStatus> allowedTransitions, TicketPermissions permissions);

    UserRef toRef(User user);

    TicketUser toTicketUser(User user);
}
