/**
 * Copyright (c) 2014 by Wen Yu.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Any modifications to this file must keep this entire header intact.
 */

package cafe.image.compression;

public interface ImageDecoder {
	/**
	 * @param pix buffer to put decoded data
	 * @param offset offset to start put decoded data
	 * @param len total number of bytes to decode
	 * @return number of pixels decoded
	 * @throws Exception
	 */
	public int decode(byte[] pix, int offset, int len) throws Exception;
}