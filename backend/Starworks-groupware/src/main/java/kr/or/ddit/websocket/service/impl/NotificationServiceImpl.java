package kr.or.ddit.websocket.service.impl;

import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import kr.or.ddit.alarm.log.service.AlarmLogService;
import kr.or.ddit.alarm.template.service.AlarmTemplateService;
import kr.or.ddit.mybatis.mapper.AuthorizationDocumentMapper;
import kr.or.ddit.mybatis.mapper.BoardMapper;
import kr.or.ddit.mybatis.mapper.EmailContentMapper;
import kr.or.ddit.mybatis.mapper.MainTaskMapper;
import kr.or.ddit.mybatis.mapper.ProjectMapper;
import kr.or.ddit.vo.AlarmLogVO;
import kr.or.ddit.vo.AlarmTemplateVO;
import kr.or.ddit.vo.AuthorizationDocumentVO;
import kr.or.ddit.vo.BoardVO;
import kr.or.ddit.vo.EmailContentVO;
import kr.or.ddit.vo.MainTaskVO;
import kr.or.ddit.vo.ProjectVO;
import kr.or.ddit.websocket.dto.NotificationTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl {

    private static final String DEFAULT_MESSAGE = "?덈줈???뚮┝???꾩갑?덉뒿?덈떎.";
    private static final String DEFAULT_URL = "#";

    private final SimpMessagingTemplate messagingTemplate;
    private final AlarmLogService alarmLogService;
    private final AlarmTemplateService alarmTemplateService;
    private final AuthorizationDocumentMapper approvalMapper;
    private final ProjectMapper projMapper;
    private final MainTaskMapper taskMapper;
    private final BoardMapper boardMapper;
    private final EmailContentMapper emailmapper;

    public void sendNotification(Map<String, Object> payload) {
        if (payload == null) {
            log.warn("?뚮┝ payload媛 null ?낅땲??");
            return;
        }

        String receiverId = (String) payload.get("receiverId");
        String senderId = (String) payload.get("senderId");
        String alarmCode = (String) payload.get("alarmCode");
        String customMessage = (String) payload.get("customMessage");
        String pk = (String) payload.get("pk");

        sendResolvedNotification(receiverId, senderId, resolveNotification(alarmCode, senderId, pk, customMessage));
    }

    public ResolvedNotification prepareBoardNotification(String alarmCode, String pstId, String pstTtl) {
        String template = DEFAULT_MESSAGE;
        String finalMessage = DEFAULT_MESSAGE;
        String alarmCategory = null;
        String relatedUrl = DEFAULT_URL;

        if (StringUtils.hasText(alarmCode)) {
            try {
                AlarmTemplateVO alarmTemplate = alarmTemplateService.readAlarmTemplate(alarmCode);
                template = fallback(alarmTemplate.getAlarmMessage(), DEFAULT_MESSAGE);
                finalMessage = appendTitle(template, pstTtl);
                alarmCategory = alarmTemplate.getAlarmCategory();
                relatedUrl = appendPkToUrl(alarmTemplate.getRelatedUrl(), pstId);
            } catch (RuntimeException ex) {
                log.warn("?뚮┝ ?쒗뵆由?議고쉶 ?ㅽ뙣(alarmCode={}). 湲곕낯 硫붿떆吏濡??泥댄빀?덈떎.", alarmCode, ex);
            }
        }

        return new ResolvedNotification(alarmCode, template, finalMessage, alarmCategory, relatedUrl);
    }

    public void sendResolvedNotification(String receiverId, String senderId, ResolvedNotification notification) {
        if (!StringUtils.hasText(receiverId)) {
            log.warn("?섏떊???녿뒗 ?뚮┝? ?꾩넚?섏? ?딆뒿?덈떎. alarmCode={}, senderId={}", notification == null ? null : notification.alarmCode(), senderId);
            return;
        }
        if (notification == null) {
            return;
        }

        AlarmLogVO alarmLog = new AlarmLogVO();
        alarmLog.setReceiverId(receiverId);
        alarmLog.setSenderId(senderId);
        alarmLog.setAlarmCode(notification.alarmCode());
        alarmLog.setAlarmMessage(notification.finalMessage());
        alarmLog.setAlarmCategory(notification.alarmCategory());
        alarmLog.setRelatedUrl(notification.relatedUrl());

        try {
            alarmLogService.createAlarmLog(alarmLog);
        } catch (RuntimeException ex) {
            log.error("?뚮┝ 濡쒓렇 ????ㅽ뙣(receiverId={}, alarmCode={})", receiverId, notification.alarmCode(), ex);
        }

        try {
            messagingTemplate.convertAndSend(
                "/topic/notify/" + receiverId,
                new NotificationTemplate(
                    receiverId,
                    senderId,
                    notification.alarmCode(),
                    notification.template(),
                    notification.finalMessage(),
                    notification.alarmCategory(),
                    notification.relatedUrl()
                )
            );
        } catch (RuntimeException ex) {
            log.error("?뱀냼耳??뚮┝ ?꾩넚 ?ㅽ뙣(receiverId={}, alarmCode={})", receiverId, notification.alarmCode(), ex);
        }
    }

    private ResolvedNotification resolveNotification(String alarmCode, String senderId, String pk, String customMessage) {
        String template = DEFAULT_MESSAGE;
        String finalMessage = StringUtils.hasText(customMessage) ? customMessage : DEFAULT_MESSAGE;
        String alarmCategory = null;
        String relatedUrl = DEFAULT_URL;

        if (StringUtils.hasText(alarmCode)) {
            try {
                AlarmTemplateVO alarmTemplate = alarmTemplateService.readAlarmTemplate(alarmCode);
                template = fallback(alarmTemplate.getAlarmMessage(), DEFAULT_MESSAGE);
                alarmCategory = alarmTemplate.getAlarmCategory();
                finalMessage = StringUtils.hasText(customMessage)
                    ? customMessage
                    : buildFinalMessage(alarmTemplate, senderId, pk);
                relatedUrl = buildRelatedUrl(alarmTemplate, senderId, pk);
            } catch (RuntimeException ex) {
                log.warn("?뚮┝ ?쒗뵆由?議고쉶 ?ㅽ뙣(alarmCode={}). 湲곕낯 硫붿떆吏濡??泥댄빀?덈떎.", alarmCode, ex);
            }
        }

        return new ResolvedNotification(alarmCode, template, finalMessage, alarmCategory, relatedUrl);
    }

    private String buildFinalMessage(AlarmTemplateVO alarmTemplate, String senderId, String pk) {
        String template = fallback(alarmTemplate.getAlarmMessage(), DEFAULT_MESSAGE);
        String alarmCode = alarmTemplate.getAlarmCode();
        if (alarmCode == null || alarmCode.isBlank()) {
            return template;
        }

        if (alarmCode.contains("APPROVAL")) {
            AuthorizationDocumentVO adVO = approvalMapper.selectAuthDocument(pk, senderId);
            return appendTitle(template, adVO != null ? adVO.getAtrzDocTtl() : null);
        }

        if (alarmCode.contains("PROJ")) {
            ProjectVO pVO = projMapper.selectProject(pk);
            return appendTitle(template, pVO != null ? pVO.getBizNm() : null);
        }

        if (alarmCode.contains("TASK")) {
            MainTaskVO mtVO = taskMapper.selectMainTask(pk);
            return appendTitle(template, mtVO != null ? mtVO.getTaskNm() : null);
        }

        if (alarmCode.contains("BOARD")) {
            BoardVO bVO = boardMapper.selectBoard(pk);
            return appendTitle(template, bVO != null ? bVO.getPstTtl() : null);
        }

        if (alarmCode.contains("MAIL")) {
            EmailContentVO emVO = emailmapper.selectEmailContent(pk);
            return appendTitle(template, emVO != null ? emVO.getSubject() : null);
        }

        return template;
    }

    private String buildRelatedUrl(AlarmTemplateVO alarmTemplate, String senderId, String pk) {
        String relatedUrl = fallback(alarmTemplate.getRelatedUrl(), DEFAULT_URL);
        String alarmCode = alarmTemplate.getAlarmCode();
        if (alarmCode == null || alarmCode.isBlank()) {
            return relatedUrl;
        }

        if (alarmCode.contains("APPROVAL")) {
            AuthorizationDocumentVO adVO = approvalMapper.selectAuthDocument(pk, senderId);
            return adVO != null ? relatedUrl + "/detail/" + adVO.getAtrzDocId() : relatedUrl;
        }

        if (alarmCode.contains("PROJ")) {
            ProjectVO pVO = projMapper.selectProject(pk);
            return pVO != null ? relatedUrl + "/" + pVO.getBizId() : relatedUrl;
        }

        if (alarmCode.contains("TASK")) {
            MainTaskVO mtVO = taskMapper.selectMainTask(pk);
            return mtVO != null ? relatedUrl + "/" + mtVO.getBizId() : relatedUrl;
        }

        if (alarmCode.contains("BOARD")) {
            BoardVO bVO = boardMapper.selectBoard(pk);
            return bVO != null ? relatedUrl + "/" + bVO.getPstId() : relatedUrl;
        }

        if (alarmCode.contains("MAIL")) {
            EmailContentVO emVO = emailmapper.selectEmailContent(pk);
            return emVO != null ? relatedUrl + "/detail/" + emVO.getEmailContId() : relatedUrl;
        }

        return relatedUrl;
    }

    private String appendTitle(String base, String title) {
        if (title == null || title.isBlank()) {
            return base;
        }
        return base + "<br/><p class=\"notification-subtitle text-sm\">" + title + "</p>";
    }

    private String appendPkToUrl(String baseUrl, String pk) {
        String resolvedBase = fallback(baseUrl, DEFAULT_URL);
        if (!StringUtils.hasText(pk) || DEFAULT_URL.equals(resolvedBase)) {
            return resolvedBase;
        }
        return resolvedBase.endsWith("/") ? resolvedBase + pk : resolvedBase + "/" + pk;
    }

    private String fallback(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    public record ResolvedNotification(
        String alarmCode,
        String template,
        String finalMessage,
        String alarmCategory,
        String relatedUrl
    ) {
    }
}
