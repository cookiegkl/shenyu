package org.apache.shenyu.examples.dubbo.api.service;

import com.google.protobuf.Empty;

public interface DubboProtobufService {

    DubboTestProtobuf insert(final DubboTestProtobuf request);

    Empty update(final DubboTestProtobuf request);

    DubboTestProtobuf findOne(final Empty request);
}
