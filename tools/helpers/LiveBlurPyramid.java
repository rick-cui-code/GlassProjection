package io.github.sixzleo.tabfold.probe;

import android.opengl.*;
import java.nio.FloatBuffer;

/** Reusable real-time Gaussian levels. No readback, buffer resizing or source-frame waits. */
final class LiveBlurPyramid {
    static final int LEVELS=7;
    static final float CONTENT_SCALE=.88f;
    final int[] levels=new int[LEVELS],scratch=new int[LEVELS],sizes=new int[LEVELS];
    final int framebuffer,copy,blur;
    final FloatBuffer vertices;
    // Black padding is filtered with the source, so the paper boundary itself
    // blurs rather than being clipped after filtering. The output stays opaque.
    static final String COPY="#extension GL_OES_EGL_image_external : require\nprecision highp float;uniform samplerExternalOES source;uniform mat4 tex;uniform float contentScale;varying vec2 uv;void main(){vec2 p=(uv-(1.-contentScale)*.5)/contentScale;if(p.x<0.||p.y<0.||p.x>1.||p.y>1.){gl_FragColor=vec4(0.,0.,0.,1.);return;}gl_FragColor=texture2D(source,(tex*vec4(p.x,1.-p.y,0.,1.)).xy);}";
    static final String BLUR="precision highp float;uniform sampler2D source;uniform vec2 stepSize;varying vec2 uv;"
        +"vec3 read(vec2 p){return pow(texture2D(source,p).rgb,vec3(2.2));}"
        +"void main(){vec2 p=vec2(uv.x,1.-uv.y);vec3 c=read(p)*.227027027;"
        +"c+=(read(p+stepSize*1.384615385)+read(p-stepSize*1.384615385))*.316216216;"
        +"c+=(read(p+stepSize*3.230769231)+read(p-stepSize*3.230769231))*.070270270;"
        +"gl_FragColor=vec4(pow(max(c,vec3(0.)),vec3(1./2.2)),1.);}";
    LiveBlurPyramid(int size,FloatBuffer vertices){
        this.vertices=vertices;copy=program(COPY);blur=program(BLUR);
        int[] f=new int[1];GLES20.glGenFramebuffers(1,f,0);framebuffer=f[0];
        GLES20.glGenTextures(LEVELS,levels,0);GLES20.glGenTextures(LEVELS,scratch,0);
        for(int i=0;i<LEVELS;i++){
            sizes[i]=Math.max(1,(size+(1<<i)-1)>>i);
            allocate(levels[i],sizes[i]);allocate(scratch[i],sizes[i]);
        }
    }
    static int program(String fragment){
        int p=GLES20.glCreateProgram();
        int v=LiveMirrorWindowProbe.shader(GLES20.GL_VERTEX_SHADER,LiveMirrorWindowProbe.VERT);
        int f=LiveMirrorWindowProbe.shader(GLES20.GL_FRAGMENT_SHADER,fragment);
        GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);
        GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);
        int[] ok=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);
        if(ok[0]==0)throw new IllegalStateException(GLES20.glGetProgramInfoLog(p));return p;
    }
    static void allocate(int texture,int size){
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,size,size,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null);
    }
    void target(int texture,int size,int program){
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,framebuffer);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,texture,0);
        if(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)!=GLES20.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Blur framebuffer incomplete");
        GLES20.glViewport(0,0,size,size);GLES20.glUseProgram(program);
        int pos=GLES20.glGetAttribLocation(program,"pos");vertices.position(0);
        GLES20.glEnableVertexAttribArray(pos);GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,0,vertices);
    }
    void update(int external,float[] matrix){
        GLES20.glActiveTexture(GLES20.GL_TEXTURE7);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,external);
        target(levels[0],sizes[0],copy);GLES20.glUniform1i(GLES20.glGetUniformLocation(copy,"source"),7);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(copy,"contentScale"),CONTENT_SCALE);
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(copy,"tex"),1,false,matrix,0);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        for(int i=1;i<LEVELS;i++){
            target(scratch[i],sizes[i],blur);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,levels[i-1]);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(blur,"source"),0);
            GLES20.glUniform2f(GLES20.glGetUniformLocation(blur,"stepSize"),1f/sizes[i],0);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
            target(levels[i],sizes[i],blur);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,scratch[i]);
            GLES20.glUniform2f(GLES20.glGetUniformLocation(blur,"stepSize"),0,1f/sizes[i]);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0);
    }
    void bind(int program,int width,int height){
        GLES20.glUseProgram(program);GLES20.glViewport(0,0,width,height);
        int pos=GLES20.glGetAttribLocation(program,"pos");vertices.position(0);
        GLES20.glEnableVertexAttribArray(pos);GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,0,vertices);
        for(int i=0;i<LEVELS;i++){GLES20.glActiveTexture(GLES20.GL_TEXTURE0+i);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,levels[i]);GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"level"+i),i);}
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"bufferSize"),sizes[0]);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"contentScale"),CONTENT_SCALE);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    }
    void close(){GLES20.glDeleteTextures(LEVELS,levels,0);GLES20.glDeleteTextures(LEVELS,scratch,0);GLES20.glDeleteFramebuffers(1,new int[]{framebuffer},0);GLES20.glDeleteProgram(copy);GLES20.glDeleteProgram(blur);}
}
