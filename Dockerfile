FROM ubuntu:16.04

MAINTAINER Joseph Burnett

EXPOSE 8080 4550 8000

RUN echo "deb http://archive.ubuntu.com/ubuntu xenial main restricted" \
    > /etc/apt/sources.list
RUN echo "deb http://archive.ubuntu.com/ubuntu xenial-updates main restricted" \
    >> /etc/apt/sources.list
RUN echo "deb http://archive.ubuntu.com/ubuntu xenial universe" \
    >> /etc/apt/sources.list
RUN echo "deb http://archive.ubuntu.com/ubuntu xenial-updates universe" \
    >> /etc/apt/sources.list
RUN echo "deb http://archive.ubuntu.com/ubuntu xenial multiverse" \
    >> /etc/apt/sources.list
RUN echo "deb http://archive.ubuntu.com/ubuntu xenial-updates multiverse" \
    >> /etc/apt/sources.list

RUN apt-get update

ENV HJ_ROOT=/hive-jam
ADD . $HJ_ROOT

RUN /bin/bash $HJ_ROOT/bin/deps-ubuntu
RUN /bin/bash $HJ_ROOT/bin/clean
RUN /bin/bash $HJ_ROOT/bin/build

CMD /bin/bash $HJ_ROOT/bin/with-gce-env launch
