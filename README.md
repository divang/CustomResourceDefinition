# This code base demonstrates how to write a Kubernetes Custom Resource Controller for Custom Resource (CRD)

Install kubemini
> https://kubernetes.io/docs/tasks/tools/install-minikube/

Install Docker. Docker Server should be in running state
> https://docs.docker.com/docker-for-mac/

Run kubemini
> minikube start --driver=docker

Compile Code
> mvn clean install

Run Kubernetes Custom Resource Controller
> mvn spring-boot:run

Create Custom Resource Definition (CRD) 
> kubectl apply -f src/main/resources/crd.yaml

Create Custom Resource Object 
> kubectl apply -f src/main/resources/cr.yaml

See the console output in above run controller
