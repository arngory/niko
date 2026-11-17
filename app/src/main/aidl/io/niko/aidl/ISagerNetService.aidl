package io.niko.aidl;

import io.niko.aidl.ISagerNetServiceCallback;

interface ISagerNetService {
  int getState();
  String getProfileName();

  void registerCallback(in ISagerNetServiceCallback cb, int id);
  oneway void unregisterCallback(in ISagerNetServiceCallback cb);

  int urlTest();

  // a profile's URL test with a core of its own, in this process, where the cores' code is in memory already (the
  // window's process would keep it): JSON out, {"ping": ms} or {"error": "…"} with "plugin" naming one not installed
  String testProfile(long profileId, String link, int timeout);
  // the tests testProfile is running end at once, each answering {"cancelled": true} (the user closed the test)
  oneway void cancelTests();

  // which rule of the running core a connection would match (libcore RouteTest): JSON in, JSON out
  String routeTest(String query);

  // the running profile's exit as a lookup service sees it (libcore ExitAddress): JSON out
  String exitAddress(String provider);
}
