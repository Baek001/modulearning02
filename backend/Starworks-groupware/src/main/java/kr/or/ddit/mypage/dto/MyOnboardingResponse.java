package kr.or.ddit.mypage.dto;

import java.util.List;

import kr.or.ddit.vo.DepartmentVO;
import kr.or.ddit.vo.JobGradeVO;
import kr.or.ddit.vo.UsersVO;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MyOnboardingResponse {
    private UsersVO user;
    private List<DepartmentVO> departments;
    private List<JobGradeVO> jobGrades;
    private boolean jobGradeFallbackRequired;
    private boolean onboardingComplete;
}
