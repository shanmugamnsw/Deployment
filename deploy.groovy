import java.text.SimpleDateFormat
import com.cloudbees.groovy.cps.NonCPS
@NonCPS
/*--------------------------------------------------------------------------------------------------------------
              valid
---------------------------------------------------------------------------------------------------------------*/
def validInput(){
  return (env.inputEnvType != '<select>') && (env.inputEnvSpace != '<select>') && (env.inputServiceList != '')
}

/*----------------------------------------------------------------------------------------------------------------------
    PIPELINE Main Function
  --------------------------------------------------------------------------------------------------------------------*/
def runPipeline(props){// Deployment start
       BUILD_TRIGGER_BY = "${currentBuild.getBuildCauses()[0].userId}"
       echo "BUILD_TRIGGER_BY: ${BUILD_TRIGGER_BY}"
       if (validInput()){
        isStaging = env.inputEnvType.equalsIgnoreCase('STG')
        isProduction = env.inputEnvType.equalsIgnoreCase('PROD')
        isInputlist = env.inputServiceList
        } 

    //if(env.inputEnvType.startsWith("STG") || (env.inputEnvType.startsWith("PROD") && (env.inputEnvSpace == "<select>"))){
       if ((env.inputEnvType == "<select>") || (env.inputSrcType == "<select>")||(env.inputnameSpace == "<select>")){
          //echo "Env is ${env.inputEnvType}"
           error "Pls provide valid input"
       }
  stage("Authoring system"){
      if (((env.inputEnvType == 'STG') || (env.inputEnvType == 'PROD')) && (!props.ldapApprovalGroup.contains(currentBuild.getBuildCauses()[0].userId))){
            error "You are not allowed to run deployment in Non-DEV environments."
            }
  }

         //if ((env.inputEnvType == "PROD") && (env.inputnameSpace != "<select>")){
 //         echo "Selected Env is ${env.inputEnvType} && Namespace is ${env.inputnameSpace}"
          //  runthistage(props)
       //     runDevDeployStages(Props)
   //      }
         if ((env.inputEnvType != "PROD") || (env.inputnameSpace != "<select>") && (env.inputDeployType != "Deployment")){
            echo "Demonset"
            runDemonsetStage(props)
         }

         if (((env.inputEnvType == 'STG') || (env.inputEnvType == 'PROD')) && (env.inputDeployType == "Deployment")){
         //if ((env.inputEnvType != "PROD") || (env.inputnameSpace != "<select>") || (env.inputDeployType == "Deployment")){
          echo "Selected Env is ${env.inputEnvType} && Namespace is ${env.inputnameSpace}"
         // runthistage(props)
          //runDevDeployStages(Props)
          }
          if (env.inputDeployType == "Deployment"){
            stage("Service-list Clollection"){
                echo "Listed total Input-services is ${env.inputServiceList}"
                }
                envNamesSplit = env.inputServiceList.tokenize(",");
                for (i = 0; i < envNamesSplit.size();i++) {
                    SelectList = envNamesSplit[i]
                    // echo " Currently Deploying this ${envNamesSplit[i]} service in ${inputEnvType}-ENVIROMENT @ ${env.inputnameSpace}-NAMESPACE"
                    runDevDeployStages(SelectList)

         }
    }
} 

def serviceValidationStages(props){
  echo "Deploying this service ${SelectList} ............. "
  lisT = props.vault
  echo "List $lisT"
  if ((env.inputnameSpace == "vault") && (!props.vault.contains("${SelectList}"))){
    echo "Your not allowed to run this $SelectList service in this ${env.inputnameSpace} namespace "
    error "Your not allowed to run this $SelectList service in this ${env.inputnameSpace} namespace"
  } else {
    echo "Allowed to run this service $SelectList in this ${env.inputnameSpace}"
  }
}


 def runDevDeployStages(SelectList){

            echo "Deploying this service ${SelectList} ............. "
             lisTUser = props.ldapApprovalGroup
             echo "lisTUser $lisTUser"
   stage("Service Validation Check"){
     serviceValidationStages(props)
   }
              stage("Regenerating Dynnamic_Values"){
                sh """#!/bin/bash +e
                cd Deployment
                pwd
                echo "DOCKER_TAG=\$(cat changeover.yaml | shyaml get-value baseImageName.$SelectList)"
                DOCKER_TAG=\$(cat changeover.yaml | shyaml get-value baseImageName.$SelectList)
                CPUMAX=\$(cat changeover.yaml | shyaml get-value cpuMax.$SelectList)
                CPUMIN=\$(cat changeover.yaml | shyaml get-value cpuMin.$SelectList)
                MEMORYMAX=\$(cat changeover.yaml | shyaml get-value memoryMax.$SelectList)
                MEMORYMIN=\$(cat changeover.yaml | shyaml get-value memoryMin.$SelectList)
                echo "===================================="
                echo "$SelectList"
                echo DOCKER_TAG is "\$DOCKER_TAG"
                echo MemoryMax "\$MEMORYMAX"
                echo MemoryMin "\$MEMORYMIN"
                echo CpuMin "\$CPUMIN"
                echo CpuMax "\$CPUMAX"
                echo "===================================="
                cd $SelectList
                sed -i "s|DYNAMIC_TAG|\$DOCKER_TAG|g" values.yaml
                sed -i "s|DYNAMIC_MEMORYMAX|\$MEMORYMAX|g" values.yaml
                sed -i "s|DYNAMIC_MEMORYMIN|\$MEMORYMIN|g" values.yaml
                sed -i "s|DYNAMIC_CPUMAX|\$CPUMAX|g" values.yaml
                sed -i "s|DYNAMIC_CPUMIN|\$CPUMIN|g" values.yaml
                cat values.yaml
                """
                stage("Helm-Deploy"){
                }
              }
 }

               // echo "sed -i 's!DYNAMIC_IMAGE!(cat changeover.yaml | shyaml get-value baseImageName.$SelectList)!g'"
                //echo "sed 's|DYNAMIC_IMAGE|'\$(cat IMG.txt)'|g'"
                //sed "s|DYNAMIC_IMAGE| '\$TAG' |g" values.yaml
                /*
                echo "\$(cat changeover.yaml | shyaml get-value baseImageName.$SelectList)" > IMG.txt
                TAG=\$(cat IMG.txt)
                echo \$TAG
                echo "sed -i 's|DYNAMIC_IMAGE|'\$TAG'|g' values.yaml"
                pwd
                sed -i "s|DYNAMIC_TAG| '\$TAG' |g" values.yaml 
                helm template . */

 def runDemonsetStage(props){
    stage(" Demonset Deploy stage"){
        echo "Deploying demonset"
    }
 }

 /*------------------------------------------------------------------------------------------------------------------------
        Approval- Stage
  -------------------------------------------------------------------------------------------------------------------------*/
  def runthistage(props){
  def deployApproved = false
  def didTimeout = false

  if ((isProduction) || (isStaging)) {
    stage("Awaiting Approval"){
      try {
        timeout(time: props.approvalTimeout, unit: props.approvalTimeoutUnit){
          userInput = input(id: 'userInput', message: "Are you Going to Proceed with this deployment in ${env.inputEnvType}-Envenvironment? along with ${env.inputServiceList}", ok: 'Deploy', submitter: props.ldapApprovalGroup)
        }
        deployApproved = true
        didTimeout = false
        }
      catch(err) {
        deployApproved = false    
        try { 
          timeout(time: props.abortReasonTimeout, unit: props.abortReasonTimeoutUnit){
            canceledReason = input(
              id: 'deployCanceledReason', message: 'Enter reason for aborting deployment: ', ok: 'ok', parameters: [string(defaultValue: '', description: '.....', name: 'Reason')]
            )
          }
          env.canceledReason = canceledReason
          echo "Deployment has been aborted. Reason: ${canceledReason}"   
        }
        catch(err2){
          env.canceledReason = "Job timed out, waiting for approval."
          echo "Deployment is aborted. Reason: ${env.canceledReason}"   
        }
      }
    }
  }
if (deployApproved == true && didTimeout == false){
stage ("Approved Given")
echo " Deployment in Staarted with approved..."
} else if (deployApproved == false && didTimeout == true){
stage (" approval not given")
echo "deployment is failed "
error "Deployment is failed"
}
}
//================================================================




/*----------------------------------------------------------------------------------------------------------------------
   DEPLOY FUNCTIONS
  --------------------------------------------------------------------------------------------------------------------*/
def runDevDeployStages1(props){
  inputServiceListSplit=env.inputServiceList.tokenize(",");
  for (list = 0; earNumber < inputServiceListSplit.size(); list++){
    currentSrcName = inputServiceListSplit[serName]
    serName = currentSrcName.tokenize(":")[0]
//    runConfigMapStages(props, currentEnv, earName)
 //   if (env.inputActivity == "ConfigMap"){
  //    deletePodsForRegeneration(props, currentEnv, earName)
   //   continue
   // }
    echo ${serName}
    //runDeployScripts(props, currentEnv)
    //stopOlderPods(props, currentEnv, earName)
    //runUATTestStages(props, currentEnv)
  }
}

return this;
