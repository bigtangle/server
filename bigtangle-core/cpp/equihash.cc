#include <jni.h>
#include <iostream>
#include <vector>
#include "net_bigtangle_equihash_EquihashSolver.h"
#include "pow.h"

using namespace _POW;

std::vector<uint32_t> deserializeIntVector(JNIEnv * env, jintArray intArray, int size) {
  std::vector<uint32_t> v;
  jint* elements = env->GetIntArrayElements(intArray, NULL);

  for(int i = 0; i < SEED_LENGTH;i++) {
    uint32_t currentInt = (uint32_t)elements[i];
    v.push_back(currentInt);
  }

  return v;
}

Seed get_seed(JNIEnv * env, jintArray intArray){
  return Seed(deserializeIntVector(env, intArray, SEED_LENGTH));
}

JNIEXPORT jobject JNICALL Java_net_bigtangle_equihash_EquihashSolver_findProof
  (JNIEnv * env, jclass clazz,  jint n, jint k, jintArray seed) {

      Equihash equihash((uint)n,(uint)k, get_seed(env,seed));
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
      jmethodID constructorID = env->GetMethodID(resultClass, "<init>", "(I[I)V");
      
      jobject result = env->NewObject(resultClass, constructorID, (jint)p.nonce, inputs);
      
      return result;
      //return result;
  }

  /*
 * Class:     net_bigtangle_equihash_EquihashSolver
 * Method:    validate
 * Signature: (II[BI[I)Z
 */
JNIEXPORT jboolean JNICALL Java_net_bigtangle_equihash_EquihashSolver_validate
  (JNIEnv *env, jclass clazz, jint n, jint k, jintArray seed, jint nonce, jintArray inputs){
    std::vector<uint32_t> inputVector = deserializeIntVector(env, inputs, SEED_LENGTH);
    Proof p = Proof((uint32_t)n, (uint32_t)k, get_seed(env, seed),(uint32_t)nonce, inputVector);
    return p.Test();
  }