<?php
/**
 * Displays content for front page
 *
 * @package WordPress
 * @subpackage Twenty_Seventeen
 * @since 1.0
 * @version 1.0
 */

?>

<div class="col-lg-12">
	<div class="card mb-4 box-shadow">
		<?php the_title( '<div class="card-header">', '</div>' ); ?>
		<div class="card-body">
			<?php
				/* translators: %s: Name of current post */
				the_content( sprintf(
					__( 'Continue reading<span class="screen-reader-text"> "%s"</span>', 'twentyseventeen' ),
					get_the_title()
				) );
			?>
		<a href="#">[read more]</a></div>
	</div>
</div>
