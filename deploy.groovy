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
       if (validInput()){
        isStaging = env.inputEnvType.equalsIgnoreCase('STG')
        isProduction = env.inputEnvType.equalsIgnoreCase('PROD')
        isInputlist = env.inputServiceList
        } 

    //if(env.inputEnvType.startsWith("STG") || (env.inputEnvType.startsWith("PROD") && (env.inputEnvSpace == "<select>"))){
       if((env.inputEnvType == "<select>") || (env.inputSrcType == "<select>")||(env.inputnameSpace == "<select>")){
          //echo "Env is ${env.inputEnvType}"
           error "Pls provide valid input"
       }

         if ((env.inputEnvType == "PROD") && (env.inputnameSpace != "<select>")){
          echo "Selected Env is ${env.inputEnvType} && Namespace is ${env.inputnameSpace}"
            runthistage(props)
       //     runDevDeployStages(Props)
         }
         
         if((env.inputEnvType != "PROD") || (env.inputnameSpace != "<select>")){
          echo "Selected Env is ${env.inputEnvType} && Namespace is ${env.inputnameSpace}"
          runthistage(props)
          //runDevDeployStages(Props)
          }
            echo "Listed total Input-services is ${env.inputServiceList}"
            envNamesSplit = env.inputServiceList.tokenize(",");
            for (i = 0; i < envNamesSplit.size();i++) {
            SelectList = envNamesSplit[i]
                        // echo " Currently Deploying this ${envNamesSplit[i]} service in ${inputEnvType}-ENVIROMENT @ ${env.inputnameSpace}-NAMESPACE"
                        runDevDeployStages(SelectList)

         }
} 

/*----------------------------------------------------------------------------------------------------------------------
   DEPLOY FUNCTIONS
  --------------------------------------------------------------------------------------------------------------------*/


 def runDevDeployStages(SelectList){

            echo "Deploying this service ${SelectList} ............. "
          //  echo "kubectl deploy ${SelectList}"
          //  echo " If required pip install shyaml"
              stage("Regenerating Dynnamic_Values"){
                sh """#!/bin/bash +e
                withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')]) {
                cd Deployment
                cat $KUBECONFIG > config
                kubectl config set-context $KUBECONFIG --namespace=sony
                echo " DOCKER_TAG=\$(cat changeover.yaml | shyaml get-value baseImageName.$SelectList)"
                echo "\$(cat changeover.yaml | shyaml get-value baseImageName.$SelectList)" > IMG.txt
                TAG=\$(cat IMG.txt)
                echo \$TAG
                cd $SelectList
                echo "sed -i 's|DYNAMIC_IMAGE|'\$TAG'|g' values.yaml"
                pwd
                sed -i "s|DYNAMIC_TAG| '\$TAG' |g" values.yaml 
                helm template .
                }
                """
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

return this;

