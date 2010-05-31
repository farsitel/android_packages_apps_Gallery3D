/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.gallery3d.ui;

import android.graphics.Color;

import javax.microedition.khronos.opengles.GL11;

class ColorTexture implements Texture {

    private int mColor;

    public ColorTexture(int color) {
        mColor = color;
    }

    public void draw(GLRootView root, int x, int y) {
    }

    public void draw(GLRootView root, int x, int y, int w, int h) {
        root.drawColor(x, y, w, h, mColor);
    }

    public boolean isOpaque() {
        int alpha = mColor >>> 24;
        return alpha != 0;
    }
}