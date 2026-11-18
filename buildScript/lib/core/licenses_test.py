#!/usr/bin/env python3
"""classify() of licenses.py on the texts that misled it once.

    python3 -I buildScript/lib/core/licenses_test.py
"""

import importlib.util
import os
import unittest

spec = importlib.util.spec_from_file_location("licenses", os.path.join(os.path.dirname(__file__), "licenses.py"))
licenses = importlib.util.module_from_spec(spec)
spec.loader.exec_module(licenses)
classify = licenses.classify

MIT = """Permission is hereby granted, free of charge, to any person obtaining a copy of this software"""
BSD_HEAD = """Redistribution and use in source and binary forms, with or without modification, are permitted provided
that the following conditions are met:
1. Redistributions of source code must retain the above copyright notice.
2. Redistributions in binary form must reproduce the above copyright notice.
"""
BSD3 = BSD_HEAD + """3. Neither the name of the copyright holder nor the names of its contributors may be used
to endorse."""


class Classify(unittest.TestCase):
    def test_mpl_names_other_licenses(self):
        # ironwood, phony, yamux: the MPL's definitions name the LGPL and the AGPL, version 3.0
        text = """Mozilla Public License Version 2.0
==================================
1.12. "Secondary License"
    means either the GNU General Public License, Version 2.0, the GNU
    Lesser General Public License, Version 2.1, the GNU Affero General
    Public License, Version 3.0, or any later versions of those licenses."""
        self.assertEqual(classify(text), ["MPL-2.0"])
        self.assertEqual(classify("Copyright (c) 2014 HashiCorp, Inc.\n\nMozilla Public License, version 2.0\n"),
                         ["MPL-2.0"])

    def test_gpl_names_the_lgpl(self):
        # mieru: the GPL's text ends on "use the GNU Lesser General Public License instead"
        text = """                    GNU GENERAL PUBLIC LICENSE
                       Version 3, 29 June 2007
  13. Use with the GNU Affero General Public License.
under version 3 of the GNU Affero General Public License into a single
consider it more useful to permit linking proprietary applications with
the library.  If this is what you want to do, use the GNU Lesser General
Public License instead of this License."""
        self.assertEqual(classify(text), ["GPL-3.0"])

    def test_gpl_notice(self):
        # sing-box and the sing modules: the notice alone
        text = """This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version."""
        self.assertEqual(classify(text), ["GPL-3.0"])

    def test_lgpl_with_exception(self):
        # yggdrasil-go
        text = """As a special exception to the GNU Lesser General Public License version 3
("LGPL3"), the copyright holders of this Library give you permission to
convey to a third party a Combined Work that links statically or dynamically
to this Library.
                   GNU LESSER GENERAL PUBLIC LICENSE
                       Version 3, 29 June 2007
  This version of the GNU Lesser General Public License incorporates
the terms and conditions of version 3 of the GNU General Public
License, supplemented by the additional permissions listed below."""
        self.assertEqual(classify(text), ["LGPL-3.0 WITH LGPL-3.0-linking-exception"])
        self.assertEqual(classify(text.split("\n", 4)[4]), ["LGPL-3.0"])

    def test_bsd_kinds(self):
        self.assertEqual(classify(BSD3), ["BSD-3-Clause"])
        self.assertEqual(classify(BSD_HEAD), ["BSD-2-Clause"])
        self.assertEqual(classify(BSD_HEAD + "3. All advertising materials mentioning features"), ["!BSD-4-Clause"])

    def test_several_in_one_file(self):
        # klauspost/compress: BSD-3 of Go's, then Apache-2.0, then MIT
        text = BSD3 + "\n\n                Apache License\n           Version 2.0, January 2004\n" \
            + "Licensed under the Apache License, Version 2.0\n\n" + MIT
        self.assertEqual(classify(text), ["BSD-3-Clause", "Apache-2.0", "MIT"])

    def test_old_gnu_licenses_are_refused(self):
        self.assertEqual(classify("GNU GENERAL PUBLIC LICENSE\nVersion 2, June 1991\n"), ["!GPL-2.0"])
        self.assertEqual(classify("GNU AFFERO GENERAL PUBLIC LICENSE\nVersion 3, 19 November 2007\n"),
                         ["!AGPL-3.0"])

    def test_others(self):
        self.assertEqual(classify("// " + MIT), ["MIT"])
        self.assertEqual(classify("Permission to use, copy, modify, and/or distribute this software for any purpose"),
                         ["ISC"])
        self.assertEqual(classify("This is free and unencumbered software released into the public domain."),
                         ["Unlicense"])
        self.assertEqual(classify("Creative Commons Legal Code\n\nCC0 1.0 Universal\n"), ["CC0-1.0"])
        zlib = "This software is provided 'as-is', without any express or implied warranty. " \
               "2. Altered source versions must be plainly marked as such"
        self.assertEqual(classify(zlib), ["Zlib"])
        self.assertEqual(classify("All rights reserved."), [])


class Repository(unittest.TestCase):
    def test_no_credentials(self):
        # what a clone URL may carry stays out of the JSON
        for url in ("https://github.com/arngory/sing-box.git",
                    "https://x-access-token:secret@github.com/arngory/sing-box.git",
                    "git@github.com:arngory/sing-box.git",
                    "https://github.com/arngory/sing-box"):
            self.assertEqual(licenses.repository_name(url), "github.com/arngory/sing-box", url)


if __name__ == "__main__":
    unittest.main()
