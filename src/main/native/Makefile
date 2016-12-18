CC=gcc
CXX=g++
RM=rm -rf

CFLAGS=-g
LDFLAGS=-g
LDLIBS=

CFLAGS=-g
CXXFLAGS=-g -std=gnu++14
LDFLAGS=-g
LDLIBS=-lboost_system -lboost_filesystem -lboost_program_options

MAIN_SRCS=mouclade.cc
MAIN_OBJS=$(subst .cc,.o,$(MAIN_SRCS))


%.o: %.cc %.h
	$(CXX) $(CXXFLAGS) -c $<

all: mouclade

mouclade: $(MAIN_OBJS)
	$(CXX) $(LDFLAGS) -o mouclade.exe $(MAIN_OBJS) $(LDLIBS)
    
clean:
	$(RM) *.o
	$(RM) mouclade.exe
