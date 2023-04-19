# FluxCD Demo Application

This repo contains a guided tutorial for [FluxCD](https://fluxcd.io/) using a Spring Boot application.

## Tutorial


### Bootstrapping FluxCD

First, we need to bootstrap FluxCD in our cluster by issuing the following command:

```shell
flux bootstrap github \
  --components-extra=image-reflector-controller,image-automation-controller \
  --owner=$GITHUB_USER \
  --repository=fluxcd-demo-cluster \
  --branch=main \
  --path=clusters/my-cluster \
  --read-write-key \
  --personal
```

### Cloning the Cluster Repository

Now, let's clone our newly-created cluster repository:

```shell
git clone git@github.com:$GITHUB_USER/fluxcd-demo-cluster.git
cd fluxcd-demo-cluster
```

### Creating a Kubernetes Deployment

We need to deploy this demo application to our cluster, so we'll need a Kubernetes deployment manifest. Let's create one:

```shell
cat <<EOF > ./clusters/my-cluster/hello-fluxcd-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: hello-fluxcd
  namespace: default
spec:
  selector:
    matchLabels:
      app: hello-fluxcd
  template:
    metadata:
      labels:
        app: hello-fluxcd
    spec:
      containers:
        - name: spring-boot
          image: ghcr.io/jwcarman/hello-fluxcd:1.0.5
          imagePullPolicy: IfNotPresent
          ports:
            - name: http
              containerPort: 8080
              protocol: TCP
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: http
            initialDelaySeconds: 1
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: http
            initialDelaySeconds: 5
            periodSeconds: 10
EOF
```

### Deploying the Application

Now, let's commit and push our changes to the cluster repository:

```shell
git add -A && \
git commit -m "add hello-fluxcd deployment" && \
git push origin main
```

At this point, we can wait a minute and let FluxCD automatically deploy the application, or we can force a 
reconciliation by issuing the following command:

```shell
flux reconcile kustomization flux-system --with-source
```

We should now see our application running in our cluster. Let's verify that:

```shell
kubectl get all
```

You should see something like this:

```shell
NAME                                READY   STATUS    RESTARTS   AGE
pod/hello-fluxcd-7bb67859db-825h7   1/1     Running   0          2m38s

NAME                 TYPE        CLUSTER-IP   EXTERNAL-IP   PORT(S)   AGE
service/kubernetes   ClusterIP   10.96.0.1    <none>        443/TCP   22m

NAME                           READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/hello-fluxcd   1/1     1            1           2m38s

NAME                                      DESIRED   CURRENT   READY   AGE
replicaset.apps/hello-fluxcd-7bb67859db   1         1         1       2m38s
```

### Configure Image Scanning

First, we must create an `ImageRepository` to let FluxCD know which container registry to scan:

```shell
flux create image repository hello-fluxcd \
--image=ghcr.io/jwcarman/hello-fluxcd \
--interval=1m \
--export > ./clusters/my-cluster/hello-fluxcd-registry.yaml
```

Next, we need to create an `ImagePolicy` to tell FluxCD how to filter the tags it finds in the container registry:

```shell
flux create image policy hello-fluxcd \
--image-ref=hello-fluxcd \
--select-semver=1.0.x \
--export > ./clusters/my-cluster/hello-fluxcd-policy.yaml
```

Again, we can commit and push our changes to the cluster repository:

```shell
git add -A && \
git commit -m "add hello-fluxcd image scan" && \
git push origin main
```

At this point (once FluxCD has reconciled the changes), we should that the `ImagePolicy` has resolved the latest tags:

```shell
flux get image policy hello-fluxcd
```

You should see something like this:

```shell
NAME        	LATEST IMAGE                       	READY	MESSAGE
hello-fluxcd	ghcr.io/jwcarman/hello-fluxcd:1.0.6	True 	Latest image tag for 'ghcr.io/jwcarman/hello-fluxcd' resolved to 1.0.6
```

### Configure Automatic Image Updates

We need to tell FluxCD where to apply automatic image updates by editing the 
`clusters/my-cluster/hello-fluxcd-deployment.yaml` file and adding a marker comment:

```yaml
    spec:
      containers:
        - name: spring-boot
          image: ghcr.io/jwcarman/hello-fluxcd:1.0.5 # {"$imagepolicy": "flux-system:hello-fluxcd"}
```

Now, we can create an `ImageUpdateAutomation` to tell FluxCD which Git repository to write image updates to:

```shell
flux create image update flux-system \
--git-repo-ref=flux-system \
--git-repo-path="./clusters/my-cluster" \
--checkout-branch=main \
--push-branch=main \
--author-name=fluxcdbot \
--author-email=fluxcdbot@users.noreply.github.com \
--commit-template="{{range .Updated.Images}}{{println .}}{{end}}" \
--export > ./clusters/my-cluster/flux-system-automation.yaml
```

Finally, we can commit and push our changes to the cluster repository:

```shell
git add -A && \
git commit -m "add image updates automation" && \
git push origin main
```

After FluxCD has reconciled the changes, we should see that the `ImageUpdateAutomation` has automatically updated our
cluster repository with the latest image tag for `hello-fluxcd`:

```shell
kubectl get deployment/hello-fluxcd -oyaml | grep 'image:'
```



