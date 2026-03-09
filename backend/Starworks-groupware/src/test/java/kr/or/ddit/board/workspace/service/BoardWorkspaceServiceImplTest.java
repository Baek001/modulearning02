package kr.or.ddit.board.workspace.service;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import kr.or.ddit.board.community.service.BoardService;
import kr.or.ddit.comm.file.service.FileDetailService;
import kr.or.ddit.comm.file.service.impl.FileDeleteServiceImpl;
import kr.or.ddit.meeting.service.MeetingReservationService;
import kr.or.ddit.mybatis.mapper.BoardCommentMapper;
import kr.or.ddit.mybatis.mapper.BoardMapper;
import kr.or.ddit.vo.BoardCommentVO;
import kr.or.ddit.vo.BoardVO;
import kr.or.ddit.websocket.service.impl.NotificationServiceImpl;

@ExtendWith(MockitoExtension.class)
class BoardWorkspaceServiceImplTest {

    @Mock
    private NamedParameterJdbcTemplate namedJdbc;
    @Mock
    private BoardService boardService;
    @Mock
    private BoardMapper boardMapper;
    @Mock
    private BoardCommentMapper boardCommentMapper;
    @Mock
    private FileDetailService fileDetailService;
    @Mock
    private FileDeleteServiceImpl fileDeleteService;
    @Mock
    private MeetingReservationService meetingReservationService;
    @Mock
    private NotificationServiceImpl notificationService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private BoardWorkspaceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BoardWorkspaceServiceImpl(
            namedJdbc,
            boardService,
            boardMapper,
            boardCommentMapper,
            fileDetailService,
            fileDeleteService,
            meetingReservationService,
            notificationService,
            eventPublisher
        );
    }

    @Test
    void deleteBoardSoftDeletesCommentsAndCleansAttachments() {
        BoardVO board = new BoardVO();
        board.setPstId("PST0001001");
        board.setBbsCtgrCd("F104");
        board.setCrtUserId("user01");

        BoardCommentVO commentWithFile = new BoardCommentVO();
        commentWithFile.setCmntSqn("101");
        commentWithFile.setCmntFileId("CMNTFILE001");

        BoardCommentVO commentWithoutFile = new BoardCommentVO();
        commentWithoutFile.setCmntSqn("102");

        when(boardMapper.selectBoard("PST0001001")).thenReturn(board);
        when(boardCommentMapper.selectBoardCommentList("PST0001001")).thenReturn(List.of(commentWithFile, commentWithoutFile));
        when(namedJdbc.queryForObject(startsWith("SELECT MEETING_RESERVATION_ID"), anyMap(), eq(Integer.class))).thenReturn(321);
        when(boardService.removeBoard("PST0001001")).thenReturn(true);

        service.deleteBoard("PST0001001", "user01", false);

        verify(meetingReservationService).removeMeetingReservation(321);
        verify(fileDeleteService).deleteFileDB("CMNTFILE001");
        verify(boardCommentMapper).softDeleteBoardCommentsByPstId("PST0001001");
        verify(boardService).removeBoard("PST0001001");
    }
}
