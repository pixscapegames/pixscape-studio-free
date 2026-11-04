float speed = pixscapeEntityFloat(PIXSCAPE_PARAM_speed);
float strength = clamp(pixscapeEntityFloat(PIXSCAPE_PARAM_strength), 0.0, 1.0);
float wave = 0.5 + 0.5 * sin(time * speed);
return vec4(base.rgb * (1.0 - strength * wave), base.a);
