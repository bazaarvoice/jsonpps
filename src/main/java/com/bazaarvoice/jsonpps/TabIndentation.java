/*
 * Copyright 2013 Bazaarvoice, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *		 http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bazaarvoice.jsonpps;

import java.io.IOException;
import java.util.Arrays;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter.NopIndenter;

@SuppressWarnings("serial")
public class TabIndentation extends NopIndenter {
	private final static String SYS_LF;
	final static int TAB_COUNT = 64;
	final static char[] TABS = new char[TAB_COUNT];
	public static final TabIndentation instance = new TabIndentation();
	protected final String _lf;

	static {
		String lf = null;
		try {
			lf = System.getProperty("line.separator");
		}
		catch(Throwable t) {
		} // access exception?
		SYS_LF = (lf == null) ? "\n" : lf;
	}

	static {
		Arrays.fill(TABS, '	');
	}

	public TabIndentation() {
		this(SYS_LF);
	}

	public TabIndentation(String lf) {
		_lf = lf;
	}

	public TabIndentation withLinefeed(String lf) {
		if(lf.equals(_lf)) {
			return this;
		}
		return new TabIndentation(lf);
	}

	@Override
	public boolean isInline() {
		return false;
	}

	@Override
	public void writeIndentation(JsonGenerator jg, int level) throws IOException, JsonGenerationException {
		jg.writeRaw(_lf);
		if(level > 0) { // should we err on negative values (as there's some flaw?)
			while(level > TAB_COUNT) { // should never happen but...
				jg.writeRaw(TABS, 0, TAB_COUNT);
				level -= TABS.length;
			}
			jg.writeRaw(TABS, 0, level);
		}
	}
}
