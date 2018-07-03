(function($) {
  "use strict"; // Start of use strict

  // Smooth scrolling using jQuery easing
  $('a.js-scroll-trigger[href*="#"]:not([href="#"])').click(function() {
    if (location.pathname.replace(/^\//, '') == this.pathname.replace(/^\//, '') && location.hostname == this.hostname) {
      var target = $(this.hash);
      target = target.length ? target : $('[name=' + this.hash.slice(1) + ']');
      if (target.length) {
        $('html, body').animate({
          scrollTop: (target.offset().top - 54)
        }, 1000, "easeInOutExpo");
        return false;
      }
    }
  });

  // Closes responsive menu when a scroll trigger link is clicked
  $('.js-scroll-trigger').click(function() {
    $('.navbar-collapse').collapse('hide');
  });

  // Activate scrollspy to add active class to navbar items on scroll
  $('body').scrollspy({
    target: '#mainNav',
    offset: 300,
  });

})(jQuery); // End of use strict

var positions = [90,150,210,270,330,30];
var active = 3;
var last = 3;
var screwInterval;
$(document).ready(function(){
  for(i = 1; i <= 6; i++){
    $('#cardFeature'+i).hide();
    $('#item'+i).click(function(){
      var id = $(this).attr('id')[4];
      screw(id);
    })
  }
  screw(1);

  $('#faq h3').click(function(){
    $(this).next().slideToggle();
    $(this).toggleClass('arrowDown');
  });
  $('.circle').css('display','block');
  $('#features .col-md-4').addClass('cardFeature');
});
run = true;
lastActive = 2;
function screw(active){
  window.clearTimeout(screwInterval);
  if(run) {
    console.log("Aktiv " + active);
    $('.cardFeature').hide();
    $('#cardFeature' + active).show();
    var teta = (lastActive - active + 6) % 6;
    for (i = 1; i <= 6; i++) {
      //i//f(teta == 5)
      positions[i - 1] += teta * 60;
      $('.arc').removeClass('arc' + i);
      if (i % 6 == active % 6) {
        $('#arc' + i).addClass('arc' + i);
      }
      animateStuff(i);

    }
    console.log(positions, teta);
    var next = active - 1 == 0 ? 6 : active - 1;
    //var next = active+1 == 7 ?1 : active+1;
    lastActive = active;
  }
  screwInterval = window.setTimeout("screw("+next+")",50000);
}

function animateStuff(i){
  var color = $('#arc'+i+':before').css('backgroundColor');
  $('#arc' + i).stop().animate({borderSpacing: positions[i - 1], backgroundColor: color}, {
    step: function (now, fx) {
      $(this).css('-webkit-transform', 'rotate(' + now + 'deg) skewX(30deg)');
      $(this).css('-moz-transform', 'rotate(' + now + 'deg) skewX(30deg)');
      $(this).css('transform', 'rotate(' + now + 'deg) skewX(30deg)');
      var angle = (now + 210) / 180 * Math.PI;
      var x = 130 + Math.cos(angle) * 100 - 16;
      var y = 130 + Math.sin(angle) * 100 - 16;
      $('#item' + i).css('left', x + 'px').css('top', y + 'px');
      $('#border' + i).css('transform', 'rotate('+now+'deg)');
    },
    duration: 'slow',
    complete: function(){
      var now = positions[i-1]%360;
      positions[i-1] = now;
      $('#arc' + i).css('-webkit-transform', 'rotate(' + now + 'deg) skewX(30deg)')
          .css('-moz-transform', 'rotate(' + now + 'deg) skewX(30deg)')
          .css('transform', 'rotate(' + now + 'deg) skewX(30deg)')
        .css('borderSpacing', now);
      $('#border' + i).css('transform', 'rotate('+now+'deg)');
      console.log(positions);
    }}, 'linear');
}





