#include <jni.h>
#include <iostream>
#include "net_bigtangle_equihash_EquihashSolver.h"
#include "pow.h"

using namespace _POW;

JNIEXPORT jobject JNICALL Java_net_bigtangle_equihash_EquihashSolver_runProofSolver
  (JNIEnv * env, jobject jthis,  jint n, jint k, jint seed) {
      Equihash equihash((uint)n,(uint)k,Seed((uint32_t)seed));
	    Proof p = equihash.FindProof();
      
      if(!p.Test()) {
        std::cout << "Invalid proof found";
        return NULL;
      }

      jint inputContent[p.inputs.size()];

      for(int i = 0; i < p.inputs.size(); i++) {
        inputContent[i] = (jint)p.inputs[i];
      }

      jintArray inputs = env->NewIntArray(p.inputs.size());      
      env->SetIntArrayRegion(inputs,0, p.inputs.size(), inputContent);

      jclass resultClass = env->FindClass("net/bigtangle/equihash/EquihashProof");
      jmethodID constructorID = env->GetMethodID(resultClass, "<init>", "(III)V");
      jobject result = env->NewObject(resultClass, constructorID, seed, (jint)p.nonce, inputs);

      return result;
  }
