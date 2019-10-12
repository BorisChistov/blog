package com.borischistov.mq;

import com.ibm.mq.MQException;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.MQConstants;
import com.ibm.mq.pcf.PCFException;
import com.ibm.mq.pcf.PCFMessage;
import com.ibm.mq.pcf.PCFMessageAgent;
import com.ibm.mq.pcf.PCFParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Optional;

/**
 * ibm mq docs - https://www.ibm.com/support/knowledgecenter/en/SSFKSJ_8.0.0/com.ibm.mq.ref.adm.doc/q086990_.htm
 */
public class Manager implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(Manager.class);

    public static void main(String[] args) throws MQException, IOException {
        try (var manager = new Manager();) {
            manager.createQueue("ARQ/1/2");
            manager.createQueue("ARQ/2/3");
            manager.createQueue("ARQ/3/4");
//            manager.createQueue("LQ/1/2");
            logger.info("----- QUEUES -----");
            manager.getQueues("*").forEach(queue -> {
                logger.info("Queue: {}, depth: {}", queue.getName(), queue.getDepth());
            });
//            manager.createTopic("T.1.2");
//            manager.createAliasForTopic("TQ/1/2", "T.1.2");
//            manager.createSubscription("ARQS/1/2", "T.1.2", "ARQ/1/2");
//            manager.createSubscription("LQS/1/2", "T.1.2", "LQ/1/2");
            logger.info("----- TOPICS -----");
            manager.getTopics("*").forEach(topic -> {
                logger.info("Topic: {}, tree string: {}", topic.getTopicName(), topic.getTopicTree());
            });

            manager.removeQueue("ARQ/1/2");
            manager.removeQueue("ARQ/2/3");
            manager.removeQueue("ARQ/3/4");
        }
    }

    private final MQQueueManager qm;
    private final PCFMessageAgent agent;

    public Manager() throws MQException {
        var connectionParams = new Hashtable<String, Object>();
        connectionParams.put(MQConstants.HOST_NAME_PROPERTY, "127.0.0.1");
        connectionParams.put(MQConstants.PORT_PROPERTY, 1414);
        connectionParams.put(MQConstants.USER_ID_PROPERTY, "mqm");
        connectionParams.put(MQConstants.PASSWORD_PROPERTY, "RfyHYSZJ");
        connectionParams.put(MQConstants.CHANNEL_PROPERTY, "SRVCHL");

        qm = new MQQueueManager("QM1", connectionParams);
        agent = new PCFMessageAgent(qm);
        agent.setCheckResponses(true);
    }

    public void removeQueue(String qName) throws MQException, IOException {
        var request = new PCFMessage(MQConstants.MQCMD_DELETE_Q);
        request.addParameter(MQConstants.MQCA_Q_NAME, qName);
        request.addParameter(MQConstants.MQIACF_PURGE, MQConstants.MQPO_YES);
        for(var response : execute(request)) {
            logParameters(response);
        }
    }

    public void createSubscription(String subsName, String tName, String qName) throws MQException, IOException {
        var request = new PCFMessage(MQConstants.MQCMD_CREATE_SUBSCRIPTION);
        request.addParameter(MQConstants.MQCACF_SUB_NAME, subsName);
        request.addParameter(MQConstants.MQCA_TOPIC_NAME, tName);
        request.addParameter(MQConstants.MQCACF_DESTINATION, qName);
        for(var response : execute(request)) {
            logParameters(response);
        }
    }

    public Queue createAliasForTopic(String aliasName, String tName) throws MQException, IOException {
        var request = new PCFMessage(MQConstants.MQCMD_CREATE_Q);
        request.addParameter(MQConstants.MQCA_Q_NAME, aliasName);
        request.addParameter(MQConstants.MQIA_Q_TYPE, MQConstants.MQQT_ALIAS);
        request.addParameter(MQConstants.MQIA_BASE_TYPE, MQConstants.MQOT_TOPIC);
        request.addParameter(MQConstants.MQCA_BASE_OBJECT_NAME, tName);
        for(var response : execute(request)) {
            logParameters(response);
        }
        return Queue.builder().name(aliasName).build();
    }

    public Queue createQueue(String qName) throws MQException, IOException {
        var request = new PCFMessage(MQConstants.MQCMD_CREATE_Q);
        request.addParameter(MQConstants.MQCA_Q_NAME, qName);
        request.addParameter(MQConstants.MQIA_Q_TYPE, MQConstants.MQQT_LOCAL);
        request.addParameter(MQConstants.MQIA_DIST_LISTS, MQConstants.MQDL_SUPPORTED);
        for(var response : execute(request)) {
            logParameters(response);
        }
        return Queue.builder().name(qName).build();
    }

    public List<Queue> getQueues(String pattern) throws MQException, IOException {
        var request = new PCFMessage(MQConstants.MQCMD_INQUIRE_Q);
        request.addParameter(MQConstants.MQCA_Q_NAME, pattern);
        request.addParameter(MQConstants.MQIACF_Q_ATTRS, new int[]{MQConstants.MQIACF_ALL});
        List<Queue> result = new ArrayList<>();
        for (var response : execute(request)) {
            logParameters(response);
            var queue = new Queue();
            extractParam(response, MQConstants.MQCA_Q_NAME, String.class).map(String::trim).ifPresent(queue::setName);
            extractParam(response, MQConstants.MQIA_CURRENT_Q_DEPTH, Integer.class).ifPresent(queue::setDepth);
            result.add(queue);
        }
        return result;
    }

    public void purgeQueue(String queueName) throws MQException, IOException {
        var request = new PCFMessage(MQConstants.MQCMD_CLEAR_Q);
        request.addParameter(MQConstants.MQCA_Q_NAME, queueName);
        for(var response : execute(request)) {
            logParameters(response);
        }
    }

    public void removeTopic(String tName) throws MQException, IOException {
        var request = new PCFMessage(MQConstants.MQCMD_DELETE_TOPIC);
        request.addParameter(MQConstants.MQCA_TOPIC_NAME, tName);
        for(var response : execute(request)) {
            logParameters(response);
        }
    }

    public void createTopic(String tName) throws MQException, IOException {
        var request = new PCFMessage(MQConstants.MQCMD_CREATE_TOPIC);
        request.addParameter(MQConstants.MQCA_TOPIC_NAME, tName);
        request.addParameter(MQConstants.MQCA_TOPIC_STRING, tName);
        for(var response : execute(request)) {
            logParameters(response);
        }
    }

    public List<Topic> getTopics(String pattern) throws MQException, IOException {
        var request = new PCFMessage(MQConstants.MQCMD_INQUIRE_TOPIC);
        request.addParameter(MQConstants.MQCA_TOPIC_NAME, pattern);
        var result = new ArrayList<Topic>();
        for(var response : execute(request)) {
            logParameters(response);
            var topic = new Topic();
            extractParam(response, MQConstants.MQCA_TOPIC_NAME, String.class).map(String::trim).ifPresent(topic::setTopicName);
            extractParam(response, MQConstants.MQCA_TOPIC_STRING, String.class).map(String::trim).ifPresent(topic::setTopicTree);
            result.add(topic);
        }

        return result;
    }

    @Override
    public void close() throws IOException {
        if(qm != null) {
            try {
                qm.close();
            } catch (MQException ignored) {

            }
        }
    }

    private PCFMessage[] execute(PCFMessage request) throws MQException, IOException {
        try {
            return agent.send(request);
        } catch (PCFException e) {
            var reason = MQConstants.lookupReasonCode(e.getReason());
            logger.warn("Error: {}, message: {}", reason, e.getMessage());
            throw e;
        }
    }

    private void logParameters(PCFMessage message) {
        if(logger.isDebugEnabled()) {
            Enumeration<?> enumeration = message.getParameters();
            while (enumeration.hasMoreElements()) {
                Object elm = enumeration.nextElement();
                if (elm instanceof PCFParameter) {
                    var param = (PCFParameter) elm;
                    logger.debug(
                            "Param name: {}, id: {}, value: {}",
                            param.getParameterName(),
                            param.getParameter(),
                            param.getValue()
                    );
                }
                else {
                    logger.trace("Param: {}", elm);
                }
            }
        }
    }

    private <T> Optional<T> extractParam(PCFMessage message, int constant, Class<T> tClass) {
        return Optional
                .ofNullable(message.getParameter(constant))
                .map(PCFParameter::getValue)
                .filter(tClass::isInstance)
                .map(tClass::cast);
    }
}
