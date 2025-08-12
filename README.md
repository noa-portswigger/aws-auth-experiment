This is a repo that contains two small tools that illustrate how to use aws credentials
to authenticate to an HTTP service.

`accept-aws-token-to-auth` is a tiny web server that displays authentication info from the calling user.

`auth-with-aws-token` is a command line client that will obtain aws credentials and pass on when calling a http service such
as accept-aws-token-to-auth.
