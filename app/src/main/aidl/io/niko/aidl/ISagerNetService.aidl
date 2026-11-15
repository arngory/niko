package io.niko.aidl;

import io.niko.aidl.ISagerNetServiceCallback;

interface ISagerNetService {
  int getState();
  String getProfileName();

  void registerCallback(in ISagerNetServiceCallback cb, int id);
  oneway void unregisterCallback(in ISagerNetServiceCallback cb);

  int urlTest();

  // which rule of the running core a connection would match (libcore RouteTest): JSON in, JSON out
  String routeTest(String query);

  // the running profile's exit as a lookup service sees it (libcore ExitAddress): JSON out
  String exitAddress(String provider);
}
