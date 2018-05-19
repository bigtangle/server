#paeq64 optimized
CC = clang++-6.0
CFLAGS = -m64 -O3 -std=c++11
EQUI = "pow.cc" "./blake/blake2b.cpp" 
JHOME = /usr/lib/jvm/java-8-openjdk-amd64
JAVAINC = -I"$(JHOME)/include" -I"$(JHOME)/include/linux" 

all: 
	#$(CC) $(CFLAGS) "pow-test.cc" $(EQUI)  -o equihash 
	$(CC) $(CFLAGS) $(JAVAINC) $(EQUI) -g -shared -fPIC  "equihash.cc" -o libequihash.so