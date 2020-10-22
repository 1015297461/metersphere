package io.metersphere.notice.service;

import io.metersphere.base.domain.MessageTask;
import io.metersphere.base.domain.MessageTaskExample;
import io.metersphere.base.domain.Notice;
import io.metersphere.base.domain.NoticeExample;
import io.metersphere.base.mapper.MessageTaskMapper;
import io.metersphere.base.mapper.NoticeMapper;
import io.metersphere.commons.constants.NoticeConstants;
import io.metersphere.notice.controller.request.MessageRequest;
import io.metersphere.notice.controller.request.NoticeRequest;
import io.metersphere.notice.domain.MessageDetail;
import io.metersphere.notice.domain.MessageSettingDetail;
import io.metersphere.notice.domain.NoticeDetail;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static io.metersphere.commons.constants.NoticeConstants.EXECUTE_FAILED;
import static io.metersphere.commons.constants.NoticeConstants.EXECUTE_SUCCESSFUL;

@Service
@Transactional(rollbackFor = Exception.class)
public class NoticeService {
    @Resource
    private NoticeMapper noticeMapper;
    @Resource
    private MessageTaskMapper messageTaskMapper;

    @Resource
    MailService mailService;

    public void saveNotice(NoticeRequest noticeRequest) {
        NoticeExample example = new NoticeExample();
        example.createCriteria().andTestIdEqualTo(noticeRequest.getTestId());
        List<Notice> notices = noticeMapper.selectByExample(example);
        if (notices.size() > 0) {
            noticeMapper.deleteByExample(example);
        }
        noticeRequest.getNotices().forEach(n -> {
            if (CollectionUtils.isNotEmpty(n.getUserIds())) {
                for (String x : n.getUserIds()) {
                    Notice notice = new Notice();
                    notice.setId(UUID.randomUUID().toString());
                    notice.setEvent(n.getEvent());
                    notice.setEnable(n.getEnable());
                    notice.setTestId(noticeRequest.getTestId());
                    notice.setUserId(x);
                    notice.setType(n.getType());
                    noticeMapper.insert(notice);
                }
            }
        });
    }

    public List<NoticeDetail> queryNotice(String id) {
        NoticeExample example = new NoticeExample();
        example.createCriteria().andTestIdEqualTo(id);
        List<Notice> notices = noticeMapper.selectByExample(example);
        List<NoticeDetail> result = new ArrayList<>();
        List<String> successList = new ArrayList<>();
        List<String> failList = new ArrayList<>();
        NoticeDetail notice1 = new NoticeDetail();
        NoticeDetail notice2 = new NoticeDetail();
        if (notices.size() > 0) {
            for (Notice n : notices) {
                if (n.getEvent().equals(EXECUTE_SUCCESSFUL)) {
                    successList.add(n.getUserId());
                    notice1.setEnable(n.getEnable());
                    notice1.setTestId(id);
                    notice1.setType(n.getType());
                    notice1.setEvent(n.getEvent());
                }
                if (n.getEvent().equals(EXECUTE_FAILED)) {
                    failList.add(n.getUserId());
                    notice2.setEnable(n.getEnable());
                    notice2.setTestId(id);
                    notice2.setType(n.getType());
                    notice2.setEvent(n.getEvent());
                }
            }
            notice1.setUserIds(successList);
            notice2.setUserIds(failList);
            result.add(notice1);
            result.add(notice2);
        }
        return result;
    }

    public void saveMessageTask(MessageRequest messageRequest) {
        String identification = UUID.randomUUID().toString();
        messageRequest.getMessageDetail().forEach(list -> {
            list.getEvents().forEach(n -> {
                list.getUserIds().forEach(m -> {
                    MessageTask message = new MessageTask();
                    message.setId(UUID.randomUUID().toString());
                    message.setEvent(n);
                    message.setTaskType(list.getTaskType());
                    message.setUserId(m);
                    message.setType(list.getType());
                    message.setWebhook(list.getWebhook());
                    message.setIdentification(identification);
                    message.setIsSet(list.getIsSet());
                    messageTaskMapper.insert(message);
                });
            });
        });


    }

    public MessageSettingDetail searchMessage() {
        MessageTaskExample messageTaskExample = new MessageTaskExample();
        messageTaskExample.createCriteria();
        List<MessageTask> messageTaskLists = new ArrayList<>();
        MessageSettingDetail messageSettingDetail = new MessageSettingDetail();
        List<MessageDetail> MessageDetailList = new ArrayList<>();
        messageTaskLists = messageTaskMapper.selectByExample(messageTaskExample);
        Map<String, List<MessageTask>> MessageTaskMap = messageTaskLists.stream().collect(Collectors.groupingBy(e -> fetchGroupKey(e)));
        MessageTaskMap.forEach((k, v) -> {
            Set userIds = new HashSet();
            Set events = new HashSet();
            MessageDetail messageDetail = new MessageDetail();
            for (MessageTask m : v) {
                userIds.add(m.getUserId());
                events.add(m.getEvent());
                messageDetail.setTaskType(m.getTaskType());
                messageDetail.setWebhook(m.getWebhook());
                messageDetail.setIdentification(m.getIdentification());
                messageDetail.setType(m.getType());
                messageDetail.setIsSet(m.getIsSet());
            }
            messageDetail.setEvents(new ArrayList(events));
            messageDetail.setUserIds(new ArrayList(userIds));
            MessageDetailList.add(messageDetail);
        });
        List<MessageDetail> jenkinsTask = MessageDetailList.stream().filter(a -> a.getTaskType().equals(NoticeConstants.JENKINS_TASK)).collect(Collectors.toList());
        List<MessageDetail> testCasePlanTask = MessageDetailList.stream().filter(a -> a.getTaskType().equals(NoticeConstants.TEST_PLAN_TASK)).collect(Collectors.toList());
        List<MessageDetail> reviewTask = MessageDetailList.stream().filter(a -> a.getTaskType().equals(NoticeConstants.REVIEW_TASK)).collect(Collectors.toList());
        List<MessageDetail> defectTask = MessageDetailList.stream().filter(a -> a.getTaskType().equals(NoticeConstants.DEFECT_TASK)).collect(Collectors.toList());
        messageSettingDetail.setJenkinsTask(jenkinsTask);
        messageSettingDetail.setTestCasePlanTask(testCasePlanTask);
        messageSettingDetail.setReviewTask(reviewTask);
        messageSettingDetail.setDefectTask(defectTask);
        return messageSettingDetail;
    }

    private static String fetchGroupKey(MessageTask user) {
        return user.getTaskType() + "#" + user.getIdentification();
    }

    public int delMessage(String identification) {
        MessageTaskExample example = new MessageTaskExample();
        example.createCriteria().andIdentificationEqualTo(identification);
        return messageTaskMapper.deleteByExample(example);
    }
/*
    public  void sendTask(List<String> userIds,String context,String taskType,String eventType){
        MessageSettingDetail messageSettingDetail = noticeService.searchMessage();
        List<MessageDetail>  taskList=new ArrayList<>();
        switch (taskType) {
            case NoticeConstants.REVIEW_TASK:
                taskList=messageSettingDetail.getReviewTask();
                break;
            case NoticeConstants.JENKINS_TASK:
                taskList=messageSettingDetail.getJenkinsTask();
                break;
            case NoticeConstants.DEFECT_TASK:
                taskList=messageSettingDetail.getDefectTask();
                break;
            case NoticeConstants.TEST_PLAN_TASK:
                taskList=messageSettingDetail.getTestCasePlanTask();
                break;
        }

        taskList.forEach(r->{
            switch (r.getType()) {
                case NoticeConstants.NAIL_ROBOT:
                    sendNailRobot(r,userIds,context,eventType);
                    break;
                case NoticeConstants.WECHAT_ROBOT:
                    sendWechatRobot(r,userIds,context,eventType);
                    break;
                case NoticeConstants.EMAIL:
                    sendEmail(r,userIds,context,eventType);
                    break;
            }
        });
    }
*/
    /*private  void sendNailRobot(MessageDetail messageDetail,List<String> userIds,String context,String eventType){
        List<String> addresseeIdList=new ArrayList<>();
        messageDetail.getEvents().forEach(e->{
            if(StringUtils.equals(eventType,e)){
                messageDetail.getUserIds().forEach(u->{
                    if(StringUtils.equals(NoticeConstants.FOUNDER,u)){
                        addresseeIdList.addAll(userIds);
                    }else{
                        addresseeIdList.add(u);
                    }
                });
                dingTaskService.sendDingTask(context, addresseeIdList,messageDetail.getWebhook());
            }
        });
    }
    private void sendWechatRobot(MessageDetail messageDetail,List<String> userIds,String context,String eventType){
        List<String> addresseeIdList=new ArrayList<>();
        messageDetail.getEvents().forEach(e->{
            if(StringUtils.equals(eventType,e)){
                messageDetail.getUserIds().forEach(u->{
                    if(StringUtils.equals(NoticeConstants.FOUNDER,u)){
                        addresseeIdList.addAll(userIds);
                    }else{
                        addresseeIdList.add(u);
                    }
                });
                wxChatTaskService.enterpriseWechatTask(context, addresseeIdList,messageDetail.getWebhook());
            }
        });
    }
        private   void sendEmail(MessageDetail messageDetail,List<String> userIds,String context,String eventType){
        List<String> addresseeIdList=new ArrayList<>();
        if(StringUtils.equals(NoticeConstants.EMAIL,messageDetail.getType())){
            messageDetail.getEvents().forEach(e->{
                if(StringUtils.equals(eventType,e)){
                    messageDetail.getUserIds().forEach(u->{
                        if(StringUtils.equals(NoticeConstants.FOUNDER,u)){
                            addresseeIdList.addAll(userIds);
                        }else{
                            addresseeIdList.add(u);
                        }
                    });
                        mailService.sendReviewerNotice(addresseeIdList, context);
                }
            });
        }

    }*/
}