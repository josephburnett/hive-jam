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
RUN apt-get install -y lsb-release

ADD . /hive-jam/
ENV HJ_ROOT=/hive-jam

RUN /hive-jam/bin/deps-ubuntu
RUN /hive-jam/bin/clean
RUN /hive-jam/bin/build

CMD /hive-jam/bin/with-gce-env launch
