package kr.or.ddit.dto.wrapper;

import kr.or.ddit.dto.FullCalendarDeptDTO;

public class DeptDTOWrapper extends FullCalendarEventDTO<FullCalendarDeptDTO> {

	public DeptDTOWrapper(FullCalendarDeptDTO adaptee) {
		super(adaptee);
	}

	@Override
	String getId() {
		return getExtendedProps().getEventId();
	}

}
