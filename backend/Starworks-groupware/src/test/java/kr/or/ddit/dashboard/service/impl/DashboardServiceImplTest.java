package kr.or.ddit.dashboard.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import kr.or.ddit.dashboard.dto.DashboardWidgetItemDTO;
import kr.or.ddit.mybatis.mapper.AuthorizationDocumentMapper;
import kr.or.ddit.mybatis.mapper.BoardCommentMapper;
import kr.or.ddit.mybatis.mapper.BoardMapper;
import kr.or.ddit.mybatis.mapper.DashboardPreferenceMapper;
import kr.or.ddit.mybatis.mapper.DepartmentMapper;
import kr.or.ddit.mybatis.mapper.DepartmentScheduleMapper;
import kr.or.ddit.mybatis.mapper.EmailBoxMapper;
import kr.or.ddit.mybatis.mapper.MainTaskMapper;
import kr.or.ddit.mybatis.mapper.ProjectMapper;
import kr.or.ddit.mybatis.mapper.TimeAndAttendanceMapper;
import kr.or.ddit.mybatis.mapper.UserHistoryMapper;
import kr.or.ddit.mybatis.mapper.UserScheduleMapper;
import kr.or.ddit.mybatis.mapper.UsersMapper;
import kr.or.ddit.vo.BoardVO;
import kr.or.ddit.vo.UsersVO;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock
    private TimeAndAttendanceMapper timeAndAttendanceMapper;
    @Mock
    private MainTaskMapper mainTaskMapper;
    @Mock
    private AuthorizationDocumentMapper approvalMapper;
    @Mock
    private EmailBoxMapper emailBoxMapper;
    @Mock
    private BoardMapper boardMapper;
    @Mock
    private BoardCommentMapper boardCommentMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private UsersMapper userMapper;
    @Mock
    private DepartmentMapper departmentMapper;
    @Mock
    private DepartmentScheduleMapper departmentScheduleMapper;
    @Mock
    private UserScheduleMapper userScheduleMapper;
    @Mock
    private DashboardPreferenceMapper dashboardPreferenceMapper;
    @Mock
    private UserHistoryMapper userHistoryMapper;

    private DashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DashboardServiceImpl(
            timeAndAttendanceMapper,
            mainTaskMapper,
            approvalMapper,
            emailBoxMapper,
            boardMapper,
            boardCommentMapper,
            projectMapper,
            userMapper,
            departmentMapper,
            departmentScheduleMapper,
            userScheduleMapper,
            dashboardPreferenceMapper,
            userHistoryMapper
        );

        UsersVO currentUser = new UsersVO();
        currentUser.setUserId("admin");
        currentUser.setDeptId("DEPT001");
        currentUser.setUserNm("Admin");

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin", ""));

        when(userMapper.selectUser("admin")).thenReturn(currentUser);
        when(departmentScheduleMapper.selectDepartmentScheduleListByDeptId("DEPT001")).thenReturn(List.of());
        when(userScheduleMapper.selectUserScheduleListByUserId("admin")).thenReturn(List.of());
        when(approvalMapper.countMyInboxCombined(anyMap())).thenReturn(0);
        when(dashboardPreferenceMapper.selectTodos("admin")).thenReturn(List.of());
        when(mainTaskMapper.selectMyTaskListNonPaging("admin")).thenReturn(List.of());
        when(dashboardPreferenceMapper.selectFavoriteUsers("admin")).thenReturn(List.of());
        when(dashboardPreferenceMapper.selectReceivedRecommendations("admin")).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getWidgetsPrioritizesPinnedNoticesAndLinksToBoardDetail() {
        when(boardMapper.selectNoticeListNonPaging()).thenReturn(List.of(
            notice("PST0000002", "Newest regular", "N", LocalDateTime.of(2026, 3, 9, 9, 0)),
            notice("PST0000001", "Pinned notice", "Y", LocalDateTime.of(2026, 3, 8, 9, 0)),
            notice("PST0000003", "Older regular", "N", LocalDateTime.of(2026, 3, 7, 9, 0))
        ));

        Map<String, Object> widgets = service.getWidgets();

        @SuppressWarnings("unchecked")
        List<DashboardWidgetItemDTO> notices = (List<DashboardWidgetItemDTO>) widgets.get("notices");

        assertEquals(3, notices.size());
        assertEquals("Pinned notice", notices.get(0).getTitle());
        assertEquals("/board?postId=PST0000001", notices.get(0).getRoute());
        assertEquals("/board?postId=PST0000002", notices.get(1).getRoute());
        assertEquals("/board?postId=PST0000003", notices.get(2).getRoute());
        assertEquals("고정", notices.get(0).getBadge());
        assertEquals("공지", notices.get(1).getBadge());
    }

    private BoardVO notice(String pstId, String title, String fixedYn, LocalDateTime createdAt) {
        BoardVO board = new BoardVO();
        board.setPstId(pstId);
        board.setPstTtl(title);
        board.setContents("Notice body");
        board.setFixedYn(fixedYn);
        board.setFrstCrtDt(createdAt);
        board.setUserNm("Admin");
        board.setCrtUserId("admin");
        return board;
    }
}
