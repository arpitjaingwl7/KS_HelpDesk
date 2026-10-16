package com.helpdesk.user;

import com.helpdesk.user.dto.UserDetail;
import com.helpdesk.user.dto.UserOption;
import com.helpdesk.user.dto.UserRef;
import com.helpdesk.user.dto.UserSummary;
import org.mapstruct.Mapper;

/** Entity → DTO mapping. Entities never leave the service layer. */
@Mapper
public interface UserMapper {

    UserSummary toSummary(User user);

    UserDetail toDetail(User user);

    UserOption toOption(User user);

    UserRef toRef(User user);
}
