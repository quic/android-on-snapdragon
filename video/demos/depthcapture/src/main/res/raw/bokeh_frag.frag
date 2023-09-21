#version 300 es

#extension GL_OES_EGL_image_external : require
#extension GL_OES_EGL_image_external_essl3 : require
#extension GL_EXT_YUV_target : require

precision mediump float;

const float BLUR_R = 0.02;

in vec2 vTextureCoord;
out vec4 gl_FragColor;

//const float DEPTH_THRESHOLD = 0.4;
uniform float uDepthThreshold;
uniform __samplerExternal2DY2YEXT sVideoTexture;
uniform __samplerExternal2DY2YEXT sDepthTexture;

vec4 getBlurColor(vec2 coord) {
  vec4 p0 = texture(sVideoTexture, coord);
  vec4 p1 = texture(sVideoTexture, vec2(coord.x+BLUR_R, coord.y));
  vec4 p2 = texture(sVideoTexture, vec2(coord.x-BLUR_R, coord.y));
  vec4 p3 = texture(sVideoTexture, vec2(coord.x, coord.y+BLUR_R));
  vec4 p4 = texture(sVideoTexture, vec2(coord.x, coord.y-BLUR_R));
  vec4 p5 = texture(sVideoTexture, vec2(coord.x+BLUR_R/2.0, coord.y+BLUR_R/2.0));
  vec4 p6 = texture(sVideoTexture, vec2(coord.x+BLUR_R/2.0, coord.y-BLUR_R/2.0));
  vec4 p7 = texture(sVideoTexture, vec2(coord.x-BLUR_R/2.0, coord.y+BLUR_R/2.0));
  vec4 p8 = texture(sVideoTexture, vec2(coord.x-BLUR_R/2.0, coord.y-BLUR_R/2.0));

//  vec4 p = (p0+p1+p2+p3+p4)/5.0;
  vec4 p = (p0+p1+p2+p3+p4+p5+p6+p7+p8)/9.0;
  return p;
}

void main() {
  vec2 texCoord = vec2((vTextureCoord.x + 1.0) / 2.0, 1.0 - (vTextureCoord.y + 1.0) / 2.0);
  vec4 depthColor = texture(sDepthTexture, texCoord);
  vec4 videoColor;

  if (depthColor.x > uDepthThreshold) {
    videoColor = texture(sVideoTexture, texCoord);
  } else {
    videoColor = getBlurColor(texCoord);
  }

  gl_FragColor = vec4(yuv_2_rgb(videoColor.xyz, itu_709), 0.0);
}
