package kr.or.ddit.mypage.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import kr.or.ddit.vo.UsersVO;

@Service
public class UserOnboardingStatusService {

    public boolean isComplete(UsersVO user) {
        return user != null
            && StringUtils.hasText(user.getUserNm())
            && StringUtils.hasText(user.getDeptId())
            && StringUtils.hasText(user.getJbgdCd())
            && StringUtils.hasText(user.getUserTelno());
    }
}
