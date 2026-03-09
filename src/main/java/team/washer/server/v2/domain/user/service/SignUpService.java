package team.washer.server.v2.domain.user.service;

import team.themoment.datagsm.sdk.oauth.model.Student;
import team.washer.server.v2.domain.user.entity.User;

public interface SignUpService {
    User execute(Student student);
}
