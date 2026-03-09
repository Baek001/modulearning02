package kr.or.ddit.mypage.service;

import kr.or.ddit.mypage.dto.MyOnboardingResponse;
import kr.or.ddit.mypage.dto.MyPasswordChangeRequest;
import kr.or.ddit.mypage.dto.MyProfileUpdateRequest;
import kr.or.ddit.vo.UsersVO;

public interface MyPageService {

    MyOnboardingResponse readOnboarding(String userId);

    UsersVO updateUserInfo(String userId, MyProfileUpdateRequest request);

    UsersVO completeOnboarding(String userId, MyProfileUpdateRequest request);

    void changePassword(String userId, MyPasswordChangeRequest request);
}
