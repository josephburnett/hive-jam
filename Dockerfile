FROM ubuntu:16.04

MAINTAINER Joseph Burnett

EXPOSE 8080 4550 8000

RUN apt-get update

ADD . /hive-jam/
ENV HJ_ROOT=/hive-jam

RUN DISTRO=Ubuntu MAJOR=16 MINOR=04 /hive-jam/bin/deps-ubuntu
RUN /hive-jam/bin/clean
RUN /hive-jam/bin/build

CMD /hive-jam/bin/with-gce-env launch
