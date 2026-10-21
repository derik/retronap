FROM ghcr.io/graalvm/native-image-community:25-ol8

USER root
RUN microdnf install -y findutils \
    && microdnf clean all
