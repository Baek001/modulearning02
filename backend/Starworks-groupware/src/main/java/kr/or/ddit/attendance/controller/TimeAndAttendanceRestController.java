package kr.or.ddit.attendance.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import kr.or.ddit.attendance.service.TimeAndAttendanceService;
import kr.or.ddit.security.CustomUserDetails;
import kr.or.ddit.vo.TimeAndAttendanceVO;
import kr.or.ddit.vo.UsersVO;
import lombok.RequiredArgsConstructor;

/**
 *
 * @author 장어진
 * @since 2025. 9. 26.
 * @see
 *
 * <pre>
 * << 개정이력(Modification Information) >>
 *
 *   수정일      			수정자           수정내용
 *  -----------   	-------------    ---------------------------
 *  2025. 9. 26.     	장어진	          최초 생성
 *  2025. 10. 15.		임가영			혹시 몰라서 쿼리스트링으로 workYmd 를 받는 readTimeAndAttendance 생성
 * </pre>
 */
@RestController
@RequestMapping("/rest/attendance")
@RequiredArgsConstructor
public class TimeAndAttendanceRestController {

	private final TimeAndAttendanceService service;

//	@GetMapping
//	public List<TimeAndAttendanceVO> readTimeAndAttendanceList(){
//		return service.readTimeAndAttendanceList();
//	}

	@GetMapping("/{userId}/{workYmd}")
	public Map<String, Object> readTimeAndAttendance(
		@PathVariable("userId") String userId
		, @PathVariable("workYmd") String workYmd
	) {
		TimeAndAttendanceVO taaVO = service.readTimeAndAttendance(userId, workYmd);

		Map<String, Object> result = new HashMap<>();
		result.put("taaVO", taaVO);
		result.put("now", LocalDateTime.now());

		return result;
	}

	@GetMapping("/{userId}/today")
	public Map<String, Object> readTimeAndAttendanceQueryString(
		@PathVariable("userId") String userId
	) {

		String workYmd = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
		TimeAndAttendanceVO taaVO = service.readTimeAndAttendance(userId, workYmd);

		Map<String, Object> result = new HashMap<>();
		result.put("taaVO", taaVO);
		result.put("now", LocalDateTime.now());

		return result;
	}

	@GetMapping("/{userId}")
	public Map<String, Object> readUserTimeAndAttendance(
			@PathVariable("userId") String userId
		) {
			List<TimeAndAttendanceVO> listTAA = service.readUserTimeAndAttendanceList(userId);

			Map<String, Object> result = new HashMap<>();
			result.put("listTAA", listTAA);
			result.put("now", LocalDateTime.now());

			return result;
		}

	@PostMapping
	public ResponseEntity<Map<String, Object>> createTimeAndAttendance(
		Authentication authentication
	){
		CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
		UsersVO realUser = userDetails.getRealUser();

		try {
			boolean success = service.createTimeAndAttendance(realUser.getUserId());
			return ResponseEntity.ok(successBody(success));
		} catch (ResponseStatusException ex) {
			return errorResponse(ex.getStatusCode(), ex.getReason());
		}
	}

	@PutMapping
	public ResponseEntity<Map<String, Object>> modifyTimeAndAttendance(
		@RequestBody TimeAndAttendanceVO taaVO
		, Authentication authentication
	){
		CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
		UsersVO realUser = userDetails.getRealUser();

		taaVO.setUserId(realUser.getUserId());
		try {
			taaVO.setWorkYmd(normalizeWorkYmd(taaVO.getWorkYmd()));
			boolean success = service.modifyTimeAndAttendance(taaVO);
			return ResponseEntity.ok(successBody(success));
		} catch (ResponseStatusException ex) {
			return errorResponse(ex.getStatusCode(), ex.getReason());
		}
	}

	private String normalizeWorkYmd(String raw) {
		if (!StringUtils.hasText(raw)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "근무일자가 필요합니다.");
		}

		String digits = raw.replaceAll("\\D", "");
		if (digits.length() < 8) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "근무일자 형식이 올바르지 않습니다.");
		}

		String normalized = digits.substring(0, 8);
		try {
			LocalDate.parse(normalized, DateTimeFormatter.BASIC_ISO_DATE);
		} catch (DateTimeParseException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "근무일자 형식이 올바르지 않습니다.", ex);
		}

		return normalized;
	}

	private ResponseEntity<Map<String, Object>> errorResponse(HttpStatusCode status, String message) {
		Map<String, Object> result = new HashMap<>();
		result.put("success", false);
		result.put("message", message);
		return ResponseEntity.status(status).body(result);
	}

	private Map<String, Object> successBody(boolean success) {
		if (!success) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "근태 처리 저장에 실패했습니다.");
		}

		Map<String, Object> result = new HashMap<>();
		result.put("success", true);
		return result;
	}
}
