package com.helpdesk.comment;

import com.helpdesk.comment.dto.CommentAuthor;
import com.helpdesk.comment.dto.CommentResponse;
import com.helpdesk.user.User;
import org.mapstruct.Mapper;

@Mapper
public interface CommentMapper {

    CommentResponse toResponse(TicketComment comment);

    CommentAuthor toAuthor(User user);
}
