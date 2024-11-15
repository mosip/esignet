# Expose an additional port, such as 5433 or any other, for a service in a specific namespace.

### steps:

1. Add the Istio Gateway and Virtual service by deploying the istio-addons and update the configuration as given below.

   * Gateway:
   ```
   spec:
     selector:
       istio: ingressgateway-internal
     servers:
       - hosts:
         - <hostname>    ## hostname will be checked only if "protocol" is set to HTTP, not for TCP protocol
       port:
         name: <PortName>
         number: <port>               
         protocol: TCP 
   ```
   
   * Virtual-service:
   ```
   gateways:
     - <gateway-name>
     hosts:
     - '*'
     tcp:
     - match:
       - port: <port>                   ## ingress gateway container port               
       route:
       - destination:
           host: <service-name>
           port: 
           number: 5432              ## pod's service port
   ```

2. Update the IstioOperator (IOP) configuration as given below by editing the IOP in the istio-system namespace.

  ```
  $ kubectl -n istio-system edit istiooperator istio-operators-mosip
  ```  

   ```
   k8s:
     service:
       ports:
         - name: <PortName>
           nodePort: <nodeport>     
           port: <port>         
           protocol: TCP
           targetPort: <port>
   ```

3. Update the configuration as given below within the `stream` block of  the nginx.conf file of nginx node. 
   ```
   upstream <backend-group-name> {
      server <server1-ip>:<nodeport>;
                server <server2-ip>:<nodeport>;
                server <server3-ip>:<nodeport>;
                server <server4-ip>:<nodeport>;
                server <server5-ip>:<nodeport>;
                server <server6-ip>:<nodeport>;
                server <server7-ip>:<nodeport>;
                server <server8-ip>:<nodeport>;

    }
   
   Note: The upstream block is usually followed by a server block where the traffic from clients is forwarded to the backend upstream group.  
   server{
        listen <cluster-nginx-internal-ip>:<port>;
        proxy_pass <backend-group-name>;
    }
   ```

4. Restart the Nginx service.
   ```
   sudo systemctl restart nginx
   ```  
   
5. Expose the port and nodePort from the AWS cloud and UFW firewall.
   * < port >: needs to be exposed for the nginx node. 
   * < nodeport >: needs to be exposed for all the k8's cluster nodes.
   
