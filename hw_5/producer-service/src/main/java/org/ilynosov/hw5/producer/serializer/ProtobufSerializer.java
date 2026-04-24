package org.ilynosov.hw5.producer.serializer;

import com.google.protobuf.Message;
import org.apache.kafka.common.serialization.Serializer;

public class ProtobufSerializer implements Serializer<Message> {

    @Override
    public byte[] serialize(String topic, Message data) {
        if (data == null) {
            return null;
        }
        return data.toByteArray();
    }
}
