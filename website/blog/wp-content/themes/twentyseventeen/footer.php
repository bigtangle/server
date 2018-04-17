<?php
/**
 * The template for displaying the footer
 *
 * Contains the closing of the #content div and all content after.
 *
 * @link https://developer.wordpress.org/themes/basics/template-files/#template-partials
 *
 * @package WordPress
 * @subpackage Twenty_Seventeen
 * @since 1.0
 * @version 1.2
 */

?>
</div>
        </div>
      </div>
	</section>

    <!-- Footer -->
    <footer class="py-5 bg-primary">
		<div class="container">
			<div class="row">
				<div class="col-lg-8 mx-auto">
						
					<h2 class="text-center">Contact</h2>
					<p class="m-0 text-center text-white">mail@bigtangle.net</p>
					<p></p>
					<p class="m-0 text-center text-white">Copyright &copy; BigTangle.net 2018</p>
				</div>
			</div>
		</div>
      <!-- /.container -->
    </footer>

    <!-- Bootstrap core JavaScript -->
    <script src="//bigtangle.net/vendor/jquery/jquery.min.js"></script>
    <script src="//bigtangle.net/vendor/bootstrap/js/bootstrap.bundle.min.js"></script>

    <!-- Plugin JavaScript -->
    <script src="//bigtangle.net/vendor/jquery-easing/jquery.easing.min.js"></script>

    <!-- Custom JavaScript for this theme -->
    <script src="//bigtangle.net/particles.js-master/particles.js"></script>
    <script src="//bigtangle.net/js/scrolling-nav.js"></script>
	<script>
		function placeFooter(){
			if(212+$('#blog .container').outerHeight()+$('nav').outerHeight()+$('footer').height() < $(window).height()){
				$('footer').css('position','fixed').css('bottom','0px').css('margin-top','0px');
				$('#blog').css('height',($(window).height()-$('footer').outerHeight()-33)+"px");
			}
			else{
				console.log("inherit");
				$('footer').css('position','inherit');
			}
		}
		$(window).resize(function(){
			placeFooter();
		});
		placeFooter();
	</script>
<?php wp_footer(); ?>
  </body>

</html>
