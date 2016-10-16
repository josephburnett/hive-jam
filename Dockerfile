FROM ubuntu:16.04

MAINTAINER Joseph Burnett

EXPOSE 8080 4550 8000

ADD sources.list /etc/apt/
RUN apt-get update

ENV HJ_ROOT=/hive-jam
ADD . $HJ_ROOT

RUN /bin/bash $HJ_ROOT/bin/deps-ubuntu
RUN /bin/bash $HJ_ROOT/bin/clean
RUN /bin/bash $HJ_ROOT/bin/build

CMD /bin/bash $HJ_ROOT/bin/with-gce-env launch
