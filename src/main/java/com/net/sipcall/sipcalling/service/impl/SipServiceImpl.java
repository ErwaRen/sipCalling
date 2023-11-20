package com.net.sipcall.sipcalling.service.impl;


import com.bzfar.exception.DataException;
import com.net.sipcall.sipcalling.config.CustomConfig;
import com.net.sipcall.sipcalling.dto.SIPDto;
import com.net.sipcall.sipcalling.exception.JudgeBusyException;
import com.net.sipcall.sipcalling.exception.UserNotInACallException;
import com.net.sipcall.sipcalling.service.SipService;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.peers.FileLogger;
import net.sourceforge.peers.javaxsound.JavaxSoundManager;
import net.sourceforge.peers.sip.Utils;
import net.sourceforge.peers.sip.core.useragent.SipListener;
import net.sourceforge.peers.sip.core.useragent.UserAgent;
import net.sourceforge.peers.sip.syntaxencoding.SipHeader;
import net.sourceforge.peers.sip.syntaxencoding.SipUriSyntaxException;
import net.sourceforge.peers.sip.transport.SipRequest;
import net.sourceforge.peers.sip.transport.SipResponse;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

@Component
@Slf4j
public class SipServiceImpl implements SipService, SipListener {

    public static final String CALLING_ACTION_STATUS = "calling"; //拨号中
    public static final String HANGUP_ACTION_STATUS = "hangup"; //挂断
    public static final String PICKUP_ACTION_STATUS = "pickup"; //通话中
    public static final String BUSY_ACTION_STATUS = "busy"; //当前法官不在线
    public static final String REGISTER_ACTION_STATUS = "registering"; //注册中

    private UserAgent userAgent;
    private SipRequest sipRequest;

    private LinkedHashMap <String, String> statusMap = new LinkedHashMap<>();

    @Override
    public void clickToDial(SIPDto sipDto) throws SocketException {


//        LambdaQueryWrapper<CallStatus> query = Wrappers.lambdaQuery();
//        query.eq(CallStatus::getName, sipDto.getName());
//        CallStatus callStatus = callStatusMapper.selectOne(query);
        log.info("当前状态 status{}",statusMap);
        if (statusMap.containsKey(sipDto.getName()) && (statusMap.get(sipDto.getName()) != HANGUP_ACTION_STATUS ||
                statusMap.get(sipDto.getName()) != BUSY_ACTION_STATUS)) {
            throw new DataException("该账号已被注册，请等待");
        }


        String peersHome = Utils.DEFAULT_PEERS_HOME;
        CustomConfig config = new CustomConfig();
        config.setUserPart(sipDto.getName());
        config.setDomain(sipDto.getIp());
        config.setPassword(sipDto.getPass());

        FileLogger logger = new FileLogger(peersHome);
        JavaxSoundManager javaxSoundManager = new JavaxSoundManager(false, logger, peersHome);
        userAgent = new UserAgent(this, config, logger, javaxSoundManager);

//        statusMap.put(sipDto.getName(), REGISTER_ACTION_STATUS);


        try {
            userAgent.register();
        } catch (SipUriSyntaxException e) {
//            e.printStackTrace();
        throw new DataException("注册失败，请稍后再试");
        }

        String callee = "sip:" + sipDto.getNumber() + "@" + sipDto.getIp();

        try {
            sipRequest = userAgent.invite(callee,
                    Utils.generateCallID(userAgent.getConfig().getLocalInetAddress()));
        } catch (SipUriSyntaxException e) {
            e.printStackTrace();
        }


//        if (callRecord != null) {
//            LambdaUpdateWrapper<CallRecord> update = Wrappers.lambdaUpdate();
//            update.set(CallRecord::getCallTimes, callRecord.getCallTimes() + 1);
//            update.eq(CallRecord::getUserCard, callRecord.getUserCard());
//            callRecordMapper.update(null, update);
//        }else {
//            callRecord = new CallRecord();
//            callRecord.setUserCard(userCard);
//            callRecord.setCallTimes(1);
//            callRecordMapper.insert(callRecord);
//        }

//        if (callStatus != null) {
//            LambdaUpdateWrapper<CallStatus> update = Wrappers.lambdaUpdate();
//            update.eq(CallStatus::getName, callStatus.getName());
//            callStatus.setStatus(1);
//            callStatusMapper.update(callStatus,update);
//        }else {
//            CallStatus status = new CallStatus();
//            status.setStatus(1);
//            status.setName(sipDto.getName());
//            callStatusMapper.insert(status);
//        }
        statusMap.put(sipDto.getName(), CALLING_ACTION_STATUS);
    }

    @Override
    public void hangUp(String name) throws SipUriSyntaxException {


//        LambdaQueryWrapper<CallStatus> query = Wrappers.lambdaQuery();
//        query.eq(CallStatus::getName, name);
//        CallStatus callStatus = callStatusMapper.selectOne(query);
        if (!statusMap.containsKey(name)) {
            throw new UserNotInACallException("用户并未通话");
        }

        statusMap.put(userAgent.getConfig().getUserPart(), HANGUP_ACTION_STATUS);
//        LambdaUpdateWrapper<CallStatus> wrapper = Wrappers.lambdaUpdate();
//        wrapper.eq(CallStatus::getName, name);
//        wrapper.set(CallStatus::getStatus, 0);
//        callStatusMapper.update(null, wrapper);

        try {
            userAgent.terminate(sipRequest);
            userAgent.unregister();
        } catch (SipUriSyntaxException e) {
            e.printStackTrace();
        }


    }

    @Override
    public void registering(SipRequest sipRequest) {

    }

    @Override
    public void registerSuccessful(UserAgent userAgent, SipResponse sipResponse) {
    }

    @Override
    public void registerFailed(UserAgent userAgent, SipResponse sipResponse) {
    }

    @Override
    public void incomingCall(SipRequest sipRequest, SipResponse provResponse) {

    }

    @Override
    public void remoteHangup(SipRequest sipRequest) throws SipUriSyntaxException {
        //获取用户name
        ArrayList<SipHeader> headers = sipRequest.getSipHeaders().getHeaders();
        SipHeader sipHeader = headers.get(2);
        String value = sipHeader.getValue().getValue();
        String name = value.substring(value.indexOf(":") + 1, value.indexOf("@"));

        statusMap.put(userAgent.getConfig().getUserPart(), HANGUP_ACTION_STATUS);

//        LambdaUpdateWrapper<CallStatus> wrapper = Wrappers.lambdaUpdate();
//        wrapper.eq(CallStatus::getName, userAgent.getConfig().getUserPart());
//        wrapper.set(CallStatus::getStatus, 0);
//        callStatusMapper.update(null, wrapper);
        try {
            userAgent.terminate(sipRequest);
            userAgent.unregister();
        } catch (SipUriSyntaxException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void ringing(SipResponse sipResponse, UserAgent userAgent) {
        statusMap.put(userAgent.getConfig().getUserPart(), CALLING_ACTION_STATUS);
    }

    @Override
    public void calleePickup(SipResponse sipResponse, UserAgent userAgent) {
        statusMap.put(userAgent.getConfig().getUserPart(), PICKUP_ACTION_STATUS);
    }

    @Override
    public void error(SipResponse sipResponse, UserAgent userAgent) throws SipUriSyntaxException {
        statusMap.put(userAgent.getConfig().getUserPart(), REGISTER_ACTION_STATUS);
        try {
            userAgent.terminate(sipRequest);
            userAgent.unregister();
        } catch (SipUriSyntaxException e) {
            e.printStackTrace();
        }

    }

    public String getStatus(String name) {

        if (statusMap.containsKey(name)) {

        }else {
            throw new UserNotInACallException("用户并未通话");
        }

        String status = statusMap.get(name);

        if (status.equals(BUSY_ACTION_STATUS)) {
            statusMap.remove(name);
            throw new JudgeBusyException("busy");
        }else if (status.equals(HANGUP_ACTION_STATUS)) {
            statusMap.remove(name);
        }

        return status;
    }

}
