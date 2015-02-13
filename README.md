# finagle-6.x-java-example
A finagle (6.6.2) example with two servers

###Current design:

*MainServer : port 9001*  
*HelperServer : port 9004*

1. JSON request from client. This can be a curl request.  
```curl -H "Content-Type: application/json" -d '{"pname": "p123"}' http://localhost:9001```  
2. On receiving the request, MainServer sends an async JSON request to HelperServer  
3. HelperServer responds to MainServer  
4. MainServer, on receiving the async response from Helper, finally sends the JSON response to client  
  
###TODO:
- [ ] Bug: Empty request sent to HelperServer  
- [ ] Actual JSON parsing

