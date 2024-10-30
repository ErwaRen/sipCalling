package com.net.sipcall.sipcalling.service.impl;


import com.net.sipcall.sipcalling.config.CustomConfig;
import com.net.sipcall.sipcalling.dto.SIPDto;
import com.net.sipcall.sipcalling.exception.BaseException;
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
    public void clickToDial(SIPDto sipDto) {
        log.info("clickToDial+++++++++++++++++++++++");
        try {
            log.info("当前状态 status{}",statusMap);
            if (statusMap.containsKey(sipDto.getName())) {
                // throw new BaseException("该账号已被注册，请等待");
            }
            String peersHome = Utils.DEFAULT_PEERS_HOME;
            CustomConfig config = new CustomConfig();
            config.setUserPart(sipDto.getName());
            config.setDomain(sipDto.getIp());
            config.setPassword(sipDto.getPass());
            FileLogger logger = new FileLogger(peersHome);
            JavaxSoundManager javaxSoundManager = new JavaxSoundManager(false, logger, peersHome);
            userAgent = new UserAgent(this, config, logger, javaxSoundManager);
            // userAgent.register(); // 不需要注册
            String callee = "sip:" + sipDto.getNumber() + "@" + sipDto.getIp();
            log.info("clickToDial+++++++++++++++++++++++ callee : {} ", callee);
            sipRequest = userAgent.invite(callee, Utils.generateCallID(userAgent.getConfig().getLocalInetAddress()));
            log.info("clickToDial+++++++++++++++++++++++ invite : success ");
        } catch (SipUriSyntaxException e) {
            log.error("clickToDial+++++++++++++++++++++++ 注册失败，请稍后再试  {}", e.getMessage(), e);
            throw new BaseException("注册失败，请稍后再试");
        } catch (Exception e) {
            log.error("clickToDial+++++++++++++++++++++++ {}", e.getMessage(), e);
            e.printStackTrace();
        }
        statusMap.put(sipDto.getName(), CALLING_ACTION_STATUS);
    }

    @Override
    public void hangUp(String name) throws SipUriSyntaxException {
        log.info("hangUp+++++++++++++++++++++++");

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
        log.info("registering+++++++++++++++++++++++");
    }

    @Override
    public void registerSuccessful(UserAgent userAgent, SipResponse sipResponse) {
        log.info("registerSuccessful+++++++++++++++++++++++");
    }

    @Override
    public void registerFailed(UserAgent userAgent, SipResponse sipResponse) {
        log.info("registerFailed+++++++++++++++++++++++");
    }

    @Override
    public void incomingCall(SipRequest sipRequest, SipResponse provResponse) {
        log.info("incomingCall+++++++++++++++++++++++");
    }

    @Override
    public void remoteHangup(SipRequest sipRequest) throws SipUriSyntaxException {
        log.info("remoteHangup+++++++++++++++++++++++");
        //获取用户name
        ArrayList<SipHeader> headers = sipRequest.getSipHeaders().getHeaders();
        SipHeader sipHeader = headers.get(2);
        String value = sipHeader.getValue().getValue();
        String name = value.substring(value.indexOf(":") + 1, value.indexOf("@"));
        statusMap.remove(userAgent.getConfig().getAuthorizationUsername());
        userAgent.terminate(sipRequest);
        userAgent.unregister();
    }

    @Override
    public void ringing(SipResponse sipResponse, UserAgent userAgent) {
        log.info("ringing+++++++++++++++++++++++");
        statusMap.put(userAgent.getConfig().getUserPart(), CALLING_ACTION_STATUS);
    }

    @Override
    public void calleePickup(SipResponse sipResponse, UserAgent userAgent) {
        log.info("calleePickup+++++++++++++++++++++++");
        statusMap.put(userAgent.getConfig().getUserPart(), PICKUP_ACTION_STATUS);
    }

    @Override
    public void error(SipResponse sipResponse, UserAgent userAgent) throws SipUriSyntaxException {
        log.info("error+++++++++++++++++++++++");
        statusMap.put(userAgent.getConfig().getUserPart(), HANGUP_ACTION_STATUS);
        statusMap.remove(userAgent.getConfig().getAuthorizationUsername());
        try {
            userAgent.terminate(sipRequest);
            userAgent.unregister();
        } catch (SipUriSyntaxException e) {
            e.printStackTrace();
        }

    }

    @Override
    public String getStatus(String name) {
        log.info("getStatus+++++++++++++++++++++++");
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
            userAgent.terminate(sipRequest);
        }

        return status;
    }

}
