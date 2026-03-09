package kr.or.ddit.board.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import kr.or.ddit.board.community.service.BoardService;
import kr.or.ddit.vo.BoardVO;

@ExtendWith(MockitoExtension.class)
class BoardRestControllerTest {

    @Mock
    private BoardService service;

    private BoardRestController controller;

    @BeforeEach
    void setUp() {
        controller = new BoardRestController(service);
    }

    @Test
    void createBoardRejectsNoticeCreationForNonAdmin() {
        BoardVO board = board("F101", "Notice title", "Notice body");

        ResponseEntity<?> response = controller.createBoard(board, authentication("user01", "ROLE_USER"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(service, never()).createBoard(any(BoardVO.class));
    }

    @Test
    void createBoardDefaultsFixedYnForAdminNotice() {
        BoardVO board = board("F101", "Notice title", "Notice body");
        when(service.createBoard(any(BoardVO.class))).thenReturn(true);

        ResponseEntity<?> response = controller.createBoard(board, authentication("admin", "ROLE_ADMIN"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("admin", board.getCrtUserId());
        assertEquals("N", board.getFixedYn());
        verify(service).createBoard(board);

        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        assertEquals(true, body.get("success"));
        assertSame(board, body.get("board"));
    }

    @Test
    void updateBoardRejectsNoticeUpdateForNonAdmin() {
        BoardVO existingBoard = board("F101", "Existing notice", "Existing body");
        existingBoard.setPstId("PST0000001");
        existingBoard.setCrtUserId("admin");
        when(service.readBoard("PST0000001")).thenReturn(existingBoard);

        BoardVO updateRequest = board("F101", "Updated title", "Updated body");

        ResponseEntity<?> response = controller.updateBoard(
            "PST0000001",
            updateRequest,
            authentication("user01", "ROLE_USER")
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(service, never()).modifyBoard(any(BoardVO.class));
    }

    private BoardVO board(String categoryCode, String title, String contents) {
        BoardVO board = new BoardVO();
        board.setBbsCtgrCd(categoryCode);
        board.setPstTtl(title);
        board.setContents(contents);
        return board;
    }

    private Authentication authentication(String userId, String... roles) {
        Collection<? extends GrantedAuthority> authorities = Arrays.stream(roles)
            .map(SimpleGrantedAuthority::new)
            .toList();
        return new UsernamePasswordAuthenticationToken(userId, "", authorities);
    }
}
