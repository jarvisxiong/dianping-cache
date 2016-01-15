package com.dianping.cache.alarm.report.task;

import com.dianping.cache.alarm.email.SpringMailSender;
import com.dianping.cache.alarm.entity.ScanDetail;
import com.dianping.cache.alarm.report.scanService.ScanDetailService;
import com.dianping.cache.alarm.utils.DateUtil;
import com.dianping.ops.http.HttpConfig;
import com.dianping.ops.http.HttpGetter;
import com.dianping.ops.http.HttpResult;
import org.apache.http.client.utils.URIBuilder;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.VelocityException;
import org.dom4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.ui.velocity.VelocityEngineFactoryBean;
import org.springframework.ui.velocity.VelocityEngineUtils;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Created by lvshiyun on 16/1/12.
 */
@Component("scanTask")
@Scope("prototype")
public class ScanTask {
    private static final long MS_PER_HOUR = 1000 * 60 * 60;
    private static final long MS_PER_DAY = MS_PER_HOUR * 24;

    @Autowired
    ScanDetailService scanDetailService;


    public void run() throws InterruptedException, DocumentException, URISyntaxException, MessagingException {
//        List<ScanDetail> scanDetails = AlarmScanDetails();


        Date now = new Date();
        String nowText = DateUtil.getCatDayString(now);

        Date yesterday = new Date(now.getTime() - MS_PER_DAY);
        String yesterdayText = DateUtil.getCatDayString(yesterday);
        List<ScanDetail> scanDetails = scanDetailService.findByCreateTime(yesterdayText);

        List<ScanDetail>failDetails = new ArrayList<ScanDetail>();
        List<ScanDetail>delayDetails = new ArrayList<ScanDetail>();

        for(ScanDetail scanDetail:scanDetails){
            if(scanDetail.getAvgValue()>10){
                delayDetails.add(scanDetail);
            }else if(scanDetail.getFailPercent()>0.1){
                failDetails.add(scanDetail);
            }
        }

        sendMail(delayDetails,failDetails);
    }


    /**
     *  * 使用Velocity模板发送邮件
     *  *
     *  * @throws MessagingException
     *  
     */
    private void sendMail(List<ScanDetail> delayDetails,List<ScanDetail>failDetails) throws MessagingException {

        SpringMailSender mailSender = new SpringMailSender();

        Properties props = System.getProperties();
        props.put("resource.loader", "class");
        props.put("class.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");

        Session session = Session.getInstance(props);

        VelocityEngineFactoryBean v = new VelocityEngineFactoryBean();
        v.setVelocityProperties(props);
        try {
            VelocityEngine velocityEngine = v.createVelocityEngine();


            // 声明Map对象，并填入用来填充模板文件的键值对
            Map<String, Object> model = new HashMap<String, Object>();
            model.put("delayDetails", delayDetails);
            model.put("failDetails", failDetails);
            // Spring提供的VelocityEngineUtils将模板进行数据填充，并转换成普通的String对象
            String emailText = VelocityEngineUtils.mergeTemplateIntoString(velocityEngine, "report.vm","utf-8", model);
            MimeMessage msg = new MimeMessage(session);

            MimeMessageHelper helper = new MimeMessageHelper(msg, true);
            helper.setFrom(mailSender.getMailSender().getUsername());
            String[] receiver =new String[]{"shiyun.lv@dianping.com","xiaoxiong.dai@dianping.com","dp.wang@dianping.com"};
            helper.setTo(receiver);
            helper.setSubject("缓存异常统计报表");

            msg.setContent(emailText, "text/html; charset=UTF-8");

            mailSender.getMailSender().send(msg);
        } catch (VelocityException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private List<ScanDetail> AlarmScanDetails() throws URISyntaxException, InterruptedException, DocumentException {

        List<ScanDetail> scanDetailList = new ArrayList<ScanDetail>();

        Date now = new Date();
        String nowText = DateUtil.getCatDayString(now);

        Date yesterday = new Date(now.getTime() - MS_PER_DAY);

        String yesterdayText = DateUtil.getCatDayString(yesterday);

        URI uri = new URIBuilder().setPath("http://cat.dianpingoa.com/cat/r/t")
                .setParameter("op", "history").setParameter("domain", "All")
                .setParameter("date", yesterdayText).setParameter("forceDownload", "xml").build();
        HttpConfig httpConfig = new HttpConfig();
        httpConfig.setRedirect(false);
        httpConfig.setTimeout(10000);
        HttpGetter HTTP_GETTER = HttpGetter.create(httpConfig);

        HttpResult httpResult = HTTP_GETTER.getWithoutException(uri);
        int count = 0;
        while ((!httpResult.isSuccess || httpResult.response == null) && (count < 5)) {
            count++;
            Thread.sleep(10000);
            httpResult = HTTP_GETTER.getWithoutException(uri);
        }
        if (httpResult.response.trim().length() == 0) {
            return null;
        }


        Document document = DocumentHelper.parseText(httpResult.response);
        org.dom4j.Element rootElement = document.getRootElement();

        org.dom4j.Element reportElement = rootElement.element("report");
        if (null == reportElement) {
            return null;
        }
        List<Element> machineElement = reportElement.elements("machine");
        if (null == machineElement) {
            return null;
        }

        for (int i = 0; i < machineElement.size(); i++) {

            List<Element> typeElements = machineElement.get(i).elements("type");
            for (int j = 0; j < typeElements.size(); j++) {
                Element element = typeElements.get(j);
                Attribute attr = element.attribute("id");
                if (attr.getStringValue().contains("Cache.") || attr.getStringValue().contains("Squirrel")) {
                    List<Element> projectElements = typeElements.get(j).elements("name");
                    for (int k = 0; k < projectElements.size(); k++) {
                        Element e = projectElements.get(k);

                        if ((Double.parseDouble(e.attribute("failPercent").getValue()) > 0.1) || (Double.parseDouble(e.attribute("avg").getValue())) > 10) {

                            ScanDetail scanDetail = new ScanDetail();
                            scanDetail.setCacheName(attr.getStringValue())
                                    .setProject(e.attribute("id").getStringValue())
                                    .setTotalCount(Integer.parseInt(e.attribute("totalCount").getValue()))
                                    .setFailCount(Integer.parseInt(e.attribute("failCount").getValue()))
                                    .setFailPercent(Double.parseDouble(e.attribute("failPercent").getValue()))
                                    .setMinValue(Double.parseDouble(e.attribute("min").getValue()))
                                    .setMaxValue(Double.parseDouble(e.attribute("max").getValue()))
                                    .setAvgValue(Double.parseDouble(e.attribute("avg").getValue()))
                                    .setSumValue(Double.parseDouble(e.attribute("sum").getValue()))
                                    .setSum2(Double.parseDouble(e.attribute("sum2").getValue()))
                                    .setStd(Double.parseDouble(e.attribute("std").getValue()))
                                    .setTps(Double.parseDouble(e.attribute("tps").getValue()))
                                    .setLine95Value(Double.parseDouble(e.attribute("line95Value").getValue()))
                                    .setLine99Value(Double.parseDouble(e.attribute("line99Value").getValue()))
                                    .setCreateTime(yesterdayText)
                                    .setUpdateTime(nowText);

                            scanDetailList.add(scanDetail);

                            scanDetailService.insert(scanDetail);

                        }
                    }
                }
            }

        }

        return scanDetailList;
    }

    public static void main(String[] args) throws InterruptedException, DocumentException, URISyntaxException, MessagingException {
        ScanTask scanTask = new ScanTask();

        scanTask.run();
    }


}

