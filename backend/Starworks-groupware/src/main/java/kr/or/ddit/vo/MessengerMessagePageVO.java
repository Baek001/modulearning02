package kr.or.ddit.vo;

import java.util.List;

import lombok.Data;

@Data
public class MessengerMessagePageVO {

    private List<MessengerContentVO> items;
    private boolean hasMore;
    private Long nextBeforeSendAt;
    private String nextBeforeMsgContId;
}