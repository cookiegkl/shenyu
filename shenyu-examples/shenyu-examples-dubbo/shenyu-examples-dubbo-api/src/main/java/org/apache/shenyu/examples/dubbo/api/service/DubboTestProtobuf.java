package org.apache.shenyu.examples.dubbo.api.service;

public class DubboTestProtobuf {

    private final String id;

    private final String name;

    private DubboTestProtobuf(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;

        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public DubboTestProtobuf build() {
            return new DubboTestProtobuf(this);
        }
    }
}
