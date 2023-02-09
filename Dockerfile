FROM openjdk:11-jre-slim-buster as stage0
LABEL snp-multi-stage="intermediate"
LABEL snp-multi-stage-id="ec620ca6-df2c-4aaa-a4ce-5d5734d6855f"
WORKDIR /opt/docker
COPY target/docker/stage/2/opt /2/opt
COPY target/docker/stage/4/opt /4/opt
USER root
RUN ["chmod", "-R", "u=rX,g=rX", "/2/opt/docker"]
RUN ["chmod", "-R", "u=rX,g=rX", "/4/opt/docker"]
RUN ["chmod", "u+x,g+x", "/4/opt/docker/bin/drt-api-import"]

FROM openjdk:11-jre-slim-buster as mainstage
USER root
RUN id -u drt 1>/dev/null 2>&1 || (( getent group 0 1>/dev/null 2>&1 || ( type groupadd 1>/dev/null 2>&1 && groupadd -g 0 root || addgroup -g 0 -S root )) && ( type useradd 1>/dev/null 2>&1 && useradd --system --create-home --uid 1001 --gid 0 drt || adduser -S -u 1001 -G root drt ))
WORKDIR /opt/docker
COPY --from=stage0 --chown=drt:root /2/opt/docker /opt/docker
COPY --from=stage0 --chown=drt:root /4/opt/docker /opt/docker

RUN apt-get update
RUN apt-get install -y curl
RUN rm -rf /var/cache/apt/*

RUN mkdir -p /home/drt/.postgresql
RUN curl https://s3.amazonaws.com/rds-downloads/rds-combined-ca-bundle.pem > /home/drt/.postgresql/root.crt

USER 1001:0
ENTRYPOINT ["/opt/docker/bin/drt-api-import"]
CMD []
