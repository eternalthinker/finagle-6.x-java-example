# finagle-6.x-java-example
A finagle (6.6.2) example with two servers

###Current design:

*MainServer : port 9001*  
*HelperServer : port 9004*

**1)** JSON request from client. This can be a curl request.  
```bash
curl -i -H "Content-Type: application/json" -d '{"pname": "p123"}' http://localhost:9001
```
**2)** On receiving the request, MainServer sends an async JSON request to HelperServer  
**3)** HelperServer responds to MainServer  
**4)** MainServer, on receiving the async response from Helper, finally sends the JSON response to client  
  
###TODO:
- [x] Bug: Empty request sent to HelperServer  

> Content is sent on adding `Content Length` header in request. But following warning is observed at MainServer  
> *Feb 13, 2015 3:08:48 PM com.twitter.jvm.Jvm$$anonfun$sample$1$2 apply*  
> *WARNING: Missed 1 collections for 0.PSScavenge due to sampling*  

- [ ] Actual JSON parsing

