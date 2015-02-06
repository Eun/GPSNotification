/*
 * Copyright (C) 2014 GPSNotification
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.osmand;

import java.io.IOException;
import java.io.InputStream;

import android.content.res.Resources;

public class GeoidAltitudeCorrection {

	private InputStream rf;
	
	private int cachedPointer = -1;
	private short cachedValue = 0;


	public GeoidAltitudeCorrection(Resources resources, int resId) {
		rf = resources.openRawResource(resId);
	}
	
	private void seek(InputStream input, int position) throws IOException
	{
		    input.reset();
		    input.skip(position);
	}
	
	public boolean isGeoidInformationAvailable(){
		return rf != null;
	}
	
	public float getGeoidHeight(double lat, double lon) {
		if (!isGeoidInformationAvailable()) {
			return 0;
		}
		int shy = (int) Math.floor((90 - lat) * 4);
		int shx = (int) Math.floor((lon >= 0 ? lon : lon + 360) * 4);
		int pointer = ((shy * 1440) + shx) * 2;
		short res = 0;
		if (pointer != cachedPointer) {
			try {
				seek(rf, pointer);
				// read short
				cachedValue = readShort();
				cachedPointer = pointer;
			} catch (IOException e) {
				//log.error("Geoid info error", e);
			}
		}
		res = cachedValue;
		return res / 100f;
	}

	private short readShort() throws IOException {
		byte[] b = new byte[2];
		rf.read(b);
		int ch1 = b[0] < 0 ? b[0] + 256 : b[0];
		int ch2 = b[1] < 0 ? b[1] + 256 : b[1];
		return (short)((ch1 << 8) + ch2);
	}
}